package com.ieltsprep.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.support.IntegrationTest;
import com.ieltsprep.support.TestAuth;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ReadingFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ItemRepository items;
    @MockitoBean ClaudeService claude;
    String auth;

    @BeforeEach
    void login() throws Exception {
        auth = TestAuth.bearer(mvc);
    }

    @Test
    void fullTestServesThreePassagesWithoutAnswersAndMarksAgainstTheKey() throws Exception {
        JsonNode session = json(mvc.perform(post("/api/reading/sessions").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"EXAM\",\"scope\":\"TEST\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(session.get("passages")).hasSize(3);
        assertThat(session.get("timeLimitSeconds").asInt()).isEqualTo(3600);
        int total = 0;
        Map<String, String> answers = new HashMap<>();
        for (JsonNode p : session.get("passages")) {
            assertThat(p.get("numberOffset").asInt()).isEqualTo(total);
            total += p.get("questionCount").asInt();
            // answers are stripped from what the browser receives
            for (JsonNode g : p.at("/passage/question_groups")) {
                for (JsonNode q : g.get("questions")) {
                    assertThat(q.get("answers")).isEmpty();
                    assertThat(q.get("justification_span").asText()).isEmpty();
                }
            }
            JsonNode full = Json.tree(items.findById(p.get("itemId").asLong()).orElseThrow().getContent());
            int offset = p.get("numberOffset").asInt();
            for (JsonNode g : full.get("question_groups")) {
                for (JsonNode q : g.get("questions")) {
                    answers.put(String.valueOf(q.get("number").asInt() + offset), q.get("answers").get(0).asText());
                }
            }
        }
        assertThat(total).isEqualTo(40);

        long id = session.get("sessionId").asLong();
        JsonNode result = json(mvc.perform(post("/api/reading/sessions/" + id + "/submit").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(Json.write(Map.of("answers", answers, "timeUsedSeconds", 3000))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.get("rawScore").asInt()).isEqualTo(40);
        assertThat(result.get("band").asDouble()).isEqualTo(9.0);
        assertThat(result.get("bandIsEstimate").asBoolean()).isFalse();
        assertThat(result.at("/passages/0/questions/0/justification").asText()).isNotBlank();

        mvc.perform(post("/api/reading/sessions/" + id + "/submit").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":{}}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/reading/sessions/" + id + "/result").header("Authorization", auth)).andExpect(status().isOk());
    }

    @Test
    void singlePassageBlankAnswersScoreZeroAndBandIsEstimated() throws Exception {
        JsonNode session = json(mvc.perform(post("/api/reading/sessions").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"PRACTICE\",\"scope\":\"PASSAGE\",\"difficulty\":2}"))
                .andReturn().getResponse().getContentAsString());
        assertThat(session.at("/passages/0/passage/difficulty").asInt()).isEqualTo(2);
        long id = session.get("sessionId").asLong();
        JsonNode result = json(mvc.perform(post("/api/reading/sessions/" + id + "/submit").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":{}}"))
                .andReturn().getResponse().getContentAsString());
        assertThat(result.get("rawScore").asInt()).isZero();
        assertThat(result.get("bandIsEstimate").asBoolean()).isTrue();
        assertThat(result.get("blanks").asInt()).isEqualTo(13);
    }

    private static JsonNode json(String body) {
        return Json.tree(body);
    }
}
