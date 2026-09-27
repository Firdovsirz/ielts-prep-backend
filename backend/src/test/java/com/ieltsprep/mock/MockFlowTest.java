package com.ieltsprep.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.claude.ClaudeCall;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import com.ieltsprep.grading.GradingModels.SpeakingGrade;
import com.ieltsprep.support.Fixtures;
import com.ieltsprep.support.IntegrationTest;
import com.ieltsprep.support.TestAuth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class MockFlowTest {

    @Autowired MockMvc mvc;
    @MockitoBean ClaudeService claude;
    String auth;

    @BeforeEach
    void login() throws Exception {
        auth = TestAuth.bearer(mvc);
        when(claude.isAvailable()).thenReturn(true);
        when(claude.call(any())).thenAnswer(inv -> {
            ClaudeCall<?> call = inv.getArgument(0);
            return call.type() == SpeakingGrade.class ? Fixtures.speakingGrade() : Fixtures.writingGrade();
        });
    }

    JsonNode call(MockHttpServletRequestBuilder req) throws Exception {
        return Json.tree(mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    long startStage(long mockId, int index, String module) throws Exception {
        JsonNode m = call(post("/api/mock/" + mockId + "/stage"));
        assertThat(m.get("stage").asText()).isEqualTo(module);
        long sid = m.at("/stages/" + index + "/sessionId").asLong();
        assertThat(sid).isPositive();
        JsonNode session = call(get("/api/" + module.toLowerCase() + "/sessions/" + sid));
        assertThat(session.get("mockTestId").asLong()).isEqualTo(mockId);
        assertThat(session.get("mode").asText()).isEqualTo("EXAM");
        // starting the stage again returns the same session (resume after a reload)
        assertThat(call(post("/api/mock/" + mockId + "/stage")).at("/stages/" + index + "/sessionId").asLong()).isEqualTo(sid);
        return sid;
    }

    @Test
    void fourPapersBackToBackProduceOneBandReport() throws Exception {
        JsonNode m = call(post("/api/mock"));
        long id = m.get("id").asLong();
        assertThat(m.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(m.get("stages").findValuesAsText("state")).containsOnly("NOT_STARTED");
        assertThat(call(post("/api/mock")).get("id").asLong()).as("resumes the mock in progress").isEqualTo(id);

        long listening = startStage(id, 0, "LISTENING");
        call(post("/api/listening/sessions/" + listening + "/submit").content(Json.write(Map.of("answers", Map.of(1, "A", 2, "B"), "timeUsedSeconds", 1900))));
        JsonNode afterListening = call(get("/api/mock/" + id));
        assertThat(afterListening.get("stage").asText()).isEqualTo("READING");
        assertThat(afterListening.at("/stages/0/state").asText()).isEqualTo("COMPLETED");
        assertThat(afterListening.at("/stages/0/band").isNull()).as("bands stay hidden until the end").isTrue();

        long reading = startStage(id, 1, "READING");
        call(post("/api/reading/sessions/" + reading + "/submit").content(Json.write(Map.of("answers", Map.of("1", "TRUE"), "timeUsedSeconds", 3500))));

        long writing = startStage(id, 2, "WRITING");
        JsonNode ws = call(get("/api/writing/sessions/" + writing));
        List<Map<String, Object>> responses = new ArrayList<>();
        ws.get("tasks").forEach(t -> responses.add(Map.of("itemId", t.get("itemId").asLong(), "text", Fixtures.ESSAY, "secondsSpent", 1500)));
        assertThat(responses).hasSize(2);
        call(post("/api/writing/sessions/" + writing + "/submit").content(Json.write(Map.of("responses", responses, "timeUsedSeconds", 3400))));

        long speaking = startStage(id, 3, "SPEAKING");
        mvc.perform(multipart("/api/speaking/sessions/" + speaking + "/responses").param("part", "1").param("questionIndex", "0")
                .param("question", "Where do you live?").param("transcript", "I live in Baku, near the seaside boulevard.")
                .param("durationSeconds", "25").header("Authorization", auth)).andExpect(status().isOk());
        call(post("/api/speaking/sessions/" + speaking + "/finish"));

        JsonNode done = call(get("/api/mock/" + id));
        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.get("stage").asText()).isEqualTo("DONE");
        assertThat(done.get("stages").findValuesAsText("grading")).contains("GRADED");
        assertThat(done.get("overall").asDouble()).isBetween(0.0, 9.0);
        JsonNode report = done.get("report");
        assertThat(report.get("bands").size()).isEqualTo(4);
        assertThat(report.at("/bands/WRITING").asDouble()).isEqualTo(6.0);
        assertThat(report.at("/bands/SPEAKING").asDouble()).isEqualTo(6.0);
        assertThat(report.get("verdict").asText()).startsWith("Overall band");
        assertThat(report.get("questionTypes").size()).isGreaterThan(0);
        assertThat(report.at("/writingCriteria/TASK2/COHERENCE_COHESION").asInt()).isEqualTo(7);
        assertThat(report.get("weaknesses").size()).isGreaterThan(0);

        assertThat(call(post("/api/mock")).get("id").asLong()).as("a finished mock is not resumed").isNotEqualTo(id);
        JsonNode list = call(get("/api/mock"));
        assertThat(list.findValuesAsText("status")).contains("COMPLETED", "IN_PROGRESS");
    }

    @Test
    void anAbandonedMockCannotContinue() throws Exception {
        long id = call(post("/api/mock")).get("id").asLong();
        assertThat(call(post("/api/mock/" + id + "/abandon")).get("status").asText()).isEqualTo("ABANDONED");
        mvc.perform(post("/api/mock/" + id + "/stage").header("Authorization", auth)).andExpect(status().isConflict());
    }
}
