package com.ieltsprep.speaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import com.ieltsprep.errorlog.ErrorEntryRepository;
import com.ieltsprep.grading.GradingModels.SpeakingGrade;
import com.ieltsprep.support.IntegrationTest;
import com.ieltsprep.support.TestAuth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SpeakingFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ErrorEntryRepository errors;
    @MockitoBean ClaudeService claude;
    String auth;

    @BeforeEach
    void login() throws Exception {
        auth = TestAuth.bearer(mvc);
    }

    static SpeakingGrade grade() {
        return com.ieltsprep.support.Fixtures.speakingGrade();
    }

    @Test
    void scriptedExaminerRunsTheWholeTestAndGradingSkipsPronunciation() throws Exception {
        when(claude.isAvailable()).thenReturn(false);
        JsonNode s = Json.tree(mvc.perform(post("/api/speaking/sessions").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"EXAM\",\"scope\":\"TEST\",\"conversational\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long id = s.get("sessionId").asLong();
        assertThat(s.at("/plan/part2/cue_card/bullets").size()).isEqualTo(3);
        assertThat(s.at("/plan/part3/linked_part2_topic").asText()).isEqualTo(s.at("/plan/part2/topic").asText());
        assertThat(s.get("examinerAvailable").asBoolean()).isFalse();

        List<String> stages = new ArrayList<>();
        for (int turn = 0; turn < 40; turn++) {
            JsonNode t = Json.tree(mvc.perform(post("/api/speaking/sessions/" + id + "/examiner").header("Authorization", auth))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            stages.add(t.get("stage").asText());
            if (t.get("stage").asText().equals("END")) {
                break;
            }
            if (turn == 0) {
                assertThat(t.get("utterance").asText()).startsWith("Good morning.");
            }
            if (t.get("stage").asText().equals("PART2_LONG_TURN")) {
                assertThat(t.get("prepSeconds").asInt()).isEqualTo(60);
                assertThat(t.get("talkSeconds").asInt()).isEqualTo(120);
            }
            var req = multipart("/api/speaking/sessions/" + id + "/responses");
            if (turn == 0) {
                req.file(new MockMultipartFile("audio", "a.webm", "audio/webm", new byte[] {26, 69, -33, -93, 1, 2, 3}));
            }
            req.param("part", t.get("part").asText()).param("questionIndex", t.get("questionIndex").asText())
                    .param("question", t.get("utterance").asText()).param("transcript", "Well I go there yesterday with my friends")
                    .param("durationSeconds", "20").header("Authorization", auth);
            mvc.perform(req).andExpect(status().isOk());
        }
        assertThat(stages).contains("PART1", "PART2_LONG_TURN", "PART2_ROUNDING", "PART3").endsWith("END");

        when(claude.call(any())).thenAnswer(inv -> grade());
        mvc.perform(post("/api/speaking/sessions/" + id + "/finish").header("Authorization", auth)).andExpect(status().isOk());
        JsonNode r = Json.tree(mvc.perform(get("/api/speaking/sessions/" + id + "/result").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString());
        assertThat(r.get("status").asText()).isEqualTo("GRADED");
        assertThat(r.get("band").asDouble()).isEqualTo(6.0);            // (7+6+6)/3 = 6.33 → 6.0; pronunciation excluded
        assertThat(r.at("/grade/pronunciation_assessable").asBoolean()).isFalse();
        assertThat(r.get("words").asInt()).isGreaterThan(20);
        long withAudio = r.get("responses").get(0).get("id").asLong();
        assertThat(r.get("responses").get(0).get("hasAudio").asBoolean()).isTrue();
        byte[] audio = mvc.perform(get("/api/speaking/responses/" + withAudio + "/audio").header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(audio).hasSize(7);
        assertThat(errors.findAll().stream().anyMatch(e -> e.getOriginal().equals("I go there yesterday"))).isTrue();
    }
}
