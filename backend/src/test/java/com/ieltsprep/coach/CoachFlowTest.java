package com.ieltsprep.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import com.ieltsprep.support.IntegrationTest;
import com.ieltsprep.support.TestAuth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class CoachFlowTest {

    @Autowired MockMvc mvc;
    @Autowired CoachService coach;
    @MockitoBean ClaudeService claude;
    String auth;

    @BeforeEach
    void login() throws Exception {
        auth = TestAuth.bearer(mvc);
    }

    JsonNode call(MockHttpServletRequestBuilder req) throws Exception {
        return Json.tree(mvc.perform(req.header("Authorization", auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void withoutAnApiKeyTheRuleBasedCoachStillWritesAReport() throws Exception {
        when(claude.isAvailable()).thenReturn(false);
        JsonNode r = call(post("/api/coach/reports"));
        assertThat(r.get("generatedBy").asText()).isEqualTo("RULES");
        assertThat(r.get("trigger").asText()).isEqualTo("MANUAL");
        assertThat(r.at("/content/headline").asText()).isNotBlank();
        assertThat(r.at("/content/priorities").size()).isEqualTo(3);
        assertThat(r.at("/stats/modules/WRITING/sessions").isInt()).isTrue();
        assertThat(r.at("/stats/plan/tasks").isInt()).isTrue();
    }

    @Test
    void claudeWritesTheReportFromTheWeeksStatistics() throws Exception {
        when(claude.isAvailable()).thenReturn(true);
        when(claude.call(any())).thenAnswer(inv -> new CoachReportContent("Writing is moving", "Summary.", "7.5 is realistic.",
                List.of("Task 2 up to 7"), List.of("Articles"), List.of(new CoachReportContent.Priority("GRAMMAR", "Drill articles", "Do it."),
                        new CoachReportContent.Priority("WRITING", "Two essays", "Do them."), new CoachReportContent.Priority("VOCAB", "Review", "Daily.")),
                "Keep going."));
        long id = call(post("/api/coach/reports")).get("id").asLong();
        JsonNode r = call(get("/api/coach/reports/" + id));
        assertThat(r.get("generatedBy").asText()).isEqualTo("AI");
        assertThat(r.at("/content/band_outlook").asText()).isEqualTo("7.5 is realistic.");
        assertThat(call(get("/api/coach/reports")).findValuesAsText("headline")).contains("Writing is moving");
    }

    @Test
    void theScheduledRunCoversTheWeekUpToYesterdayAndReplans() {
        when(claude.isAvailable()).thenReturn(false);
        coach.weekly();
        CoachDtos.ReportView latest = coach.list().getFirst();
        assertThat(latest.trigger()).isEqualTo("SCHEDULED");
        assertThat(latest.weekEnd()).isEqualTo(java.time.LocalDate.now().minusDays(1));
        assertThat(latest.weekStart()).isEqualTo(latest.weekEnd().minusDays(6));
    }
}
