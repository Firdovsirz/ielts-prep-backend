package com.ieltsprep.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import com.ieltsprep.support.IntegrationTest;
import com.ieltsprep.support.TestAuth;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@IntegrationTest
class DashboardFlowTest {

    @Autowired MockMvc mvc;
    @MockitoBean ClaudeService claude;
    String auth;

    @BeforeEach
    void login() throws Exception {
        auth = TestAuth.bearer(mvc);
    }

    private void readingSession(int difficulty) throws Exception {
        JsonNode s = Json.tree(mvc.perform(MockMvcRequestBuilders.post("/api/reading/sessions").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"PRACTICE\",\"scope\":\"PASSAGE\",\"difficulty\":" + difficulty + "}"))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(MockMvcRequestBuilders.post("/api/reading/sessions/" + s.get("sessionId").asLong() + "/submit")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(Json.write(Map.of("answers", Map.of("1", "TRUE", "2", "A"), "secondsPerPassage",
                        Map.of(String.valueOf(s.at("/passages/0/itemId").asLong()), 900)))))
                .andExpect(status().isOk());
    }

    @Test
    void dashboardAggregatesBandsQuestionTypesTimingAndActivity() throws Exception {
        readingSession(1);
        readingSession(2);
        JsonNode d = Json.tree(mvc.perform(get("/api/dashboard").header("Authorization", auth)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(d.get("targetBand").asDouble()).isGreaterThan(0);
        assertThat(d.at("/bandHistory/READING").size()).isGreaterThanOrEqualTo(2);
        JsonNode reading = d.at("/bandHistory/READING");
        assertThat(reading.get(reading.size() - 1).get("estimate").asBoolean()).isTrue(); // single passages are estimates
        assertThat(d.at("/current/reading").isNull()).isFalse();
        // the overall band exists only once all four modules have a band (other test classes share this database)
        boolean allFour = !d.at("/current/listening").isNull() && !d.at("/current/reading").isNull()
                && !d.at("/current/writing").isNull() && !d.at("/current/speaking").isNull();
        assertThat(d.at("/current/overall").isNull()).isEqualTo(!allFour);
        assertThat(d.get("questionTypes").size()).isGreaterThan(0);
        assertThat(d.at("/timing/0/label").asText()).isEqualTo("Reading passage");
        assertThat(d.at("/timing/0/averageSeconds").asDouble()).isEqualTo(900.0);
        assertThat(d.at("/activity/streakDays").asInt()).isEqualTo(1);
        assertThat(d.at("/activity/last28Days").size()).isEqualTo(28);

        JsonNode history = Json.tree(mvc.perform(get("/api/history").header("Authorization", auth)).andReturn().getResponse().getContentAsString());
        assertThat(history.get(0).get("module").asText()).isEqualTo("READING");
    }

    @Test
    void exportProducesJsonAndCsvZipWithoutCredentials() throws Exception {
        readingSession(3);
        String json = mvc.perform(get("/api/export").header("Authorization", auth)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = Json.tree(json);
        assertThat(root.get("attempts").size()).isGreaterThan(0);
        assertThat(root.at("/attempts/0/per_question").isArray()).isTrue(); // JSON columns expanded
        assertThat(json).doesNotContain("password_hash").doesNotContain("tester@example.com");

        byte[] zip = mvc.perform(get("/api/export?format=csv").header("Authorization", auth)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        Set<String> names = new HashSet<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                names.add(e.getName());
            }
        }
        assertThat(names).contains("attempts.csv", "sessions.csv", "errors.csv", "api_usage.csv", "items.csv");
    }
}
