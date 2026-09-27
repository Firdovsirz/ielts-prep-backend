package com.ieltsprep.listening;

import static org.assertj.core.api.Assertions.assertThat;
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
class ListeningFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ItemRepository items;
    @MockitoBean ClaudeService claude;
    String auth;

    @BeforeEach
    void login() throws Exception {
        auth = TestAuth.bearer(mvc);
    }

    @Test
    void fullTestHasFourSectionsOfTenAndMarksWithSpellingTolerance() throws Exception {
        JsonNode s = Json.tree(mvc.perform(post("/api/listening/sessions").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"EXAM\",\"scope\":\"TEST\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(s.get("sections")).hasSize(4);
        assertThat(s.get("readingSeconds").asInt()).isEqualTo(30);
        assertThat(s.get("transferMinutes").asInt()).isEqualTo(10);
        Map<String, String> answers = new HashMap<>();
        int expectedSection = 1;
        for (JsonNode sec : s.get("sections")) {
            assertThat(sec.get("questionCount").asInt()).isEqualTo(10);
            assertThat(sec.at("/section/section").asInt()).isEqualTo(expectedSection++);
            assertThat(sec.at("/section/script").size()).isGreaterThan(5); // spoken by the browser
            assertThat(sec.at("/section/question_groups/0/questions/0/answers")).isEmpty();
            JsonNode full = Json.tree(items.findById(sec.get("itemId").asLong()).orElseThrow().getContent());
            int offset = sec.get("numberOffset").asInt();
            for (JsonNode g : full.get("question_groups")) {
                for (JsonNode q : g.get("questions")) {
                    String key = q.get("answers").get(0).asText();
                    // British spellings typed in American form must still be accepted
                    answers.put(String.valueOf(q.get("number").asInt() + offset), key.replace("colour", "color").replace("centre", "center"));
                }
            }
        }
        JsonNode r = Json.tree(mvc.perform(post("/api/listening/sessions/" + s.get("sessionId").asLong() + "/submit")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(Json.write(Map.of("answers", answers, "timeUsedSeconds", 2400))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(r.get("rawScore").asInt()).isEqualTo(40);
        assertThat(r.get("band").asDouble()).isEqualTo(9.0);
        assertThat(r.at("/sections/3/questions/0/location").asText()).matches("\\d+");
    }
}
