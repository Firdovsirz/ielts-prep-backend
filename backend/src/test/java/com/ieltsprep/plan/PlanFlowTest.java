package com.ieltsprep.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import com.ieltsprep.content.Skill;
import com.ieltsprep.session.PracticeSession;
import com.ieltsprep.session.PracticeSessionRepository;
import com.ieltsprep.session.SessionKind;
import com.ieltsprep.session.SessionMode;
import com.ieltsprep.session.SessionStatus;
import com.ieltsprep.support.IntegrationTest;
import com.ieltsprep.support.TestAuth;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class PlanFlowTest {

    @Autowired MockMvc mvc;
    @Autowired PracticeSessionRepository sessions;
    @Autowired PlanTaskRepository tasks;
    @MockitoBean ClaudeService claude;
    String auth;

    @BeforeEach
    void login() throws Exception {
        auth = TestAuth.bearer(mvc);
    }

    JsonNode call(MockHttpServletRequestBuilder req) throws Exception {
        return Json.tree(mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void theWeekIsPlannedOnFirstAccessAndTicksOffCompletedSessions() throws Exception {
        when(claude.isAvailable()).thenReturn(false);
        assertThat(call(get("/api/plan")).get("days").size()).isEqualTo(7);
        JsonNode plan = call(post("/api/plan/regenerate?ai=true")); // no API key → rule-based
        assertThat(plan.get("days").size()).isEqualTo(7);
        assertThat(plan.at("/days/0/today").asBoolean()).isTrue();
        assertThat(plan.get("source").asText()).isEqualTo("RULES");
        assertThat(plan.get("focus").asText()).isNotBlank();
        assertThat(plan.get("aiAvailable").asBoolean()).isFalse();
        JsonNode today = plan.at("/days/0/tasks");
        assertThat(today.size()).isGreaterThan(0);

        // Complete a session matching one of today's practice tasks → it is ticked off automatically.
        JsonNode target = null;
        for (JsonNode t : today) {
            PlanAction a = PlanAction.valueOf(t.get("action").asText());
            if (!a.completedBy().isEmpty() && !t.get("done").asBoolean()) {
                target = t;
                break;
            }
        }
        if (target != null) {
            PlanAction a = PlanAction.valueOf(target.get("action").asText());
            PracticeSession s = new PracticeSession();
            s.setModule(Skill.valueOf(a.module()));
            s.setKind(a.completedBy().iterator().next());
            s.setMode(SessionMode.PRACTICE);
            s.setItemIdList(List.of());
            s.setStartedAt(Instant.now().minusSeconds(600));
            s.setFinishedAt(Instant.now());
            s.setStatus(SessionStatus.COMPLETED);
            sessions.save(s);
            long id = target.get("id").asLong();
            JsonNode again = call(get("/api/plan"));
            boolean done = false;
            for (JsonNode t : again.at("/days/0/tasks")) {
                if (t.get("id").asLong() == id) {
                    done = t.get("done").asBoolean();
                }
            }
            assertThat(done).as("auto-completed %s", a).isTrue();
        }

        long first = today.get(0).get("id").asLong();
        JsonNode toggled = call(post("/api/plan/tasks/" + first + "/toggle").content("{\"done\":true}"));
        assertThat(toggled.get("done").asBoolean()).isTrue();
        assertThat(call(post("/api/plan/tasks/" + first + "/toggle").content("{\"done\":false}")).get("done").asBoolean()).isFalse();
    }

    @Test
    void claudePersonalisesTheDraftWithinTheRules() throws Exception {
        when(claude.isAvailable()).thenReturn(true);
        when(claude.call(any())).thenAnswer(inv -> {
            List<PlanService.AiPlan.AiDay> days = new ArrayList<>();
            LocalDate d = LocalDate.now();
            for (int i = 0; i < 7; i++) {
                days.add(new PlanService.AiPlan.AiDay(d.plusDays(i).toString(), List.of(
                        new PlanService.AiPlan.AiTask("WRITING_TASK2", "", "Essay on remote work — focus on topic sentences", "Coherence 6.", 40),
                        new PlanService.AiPlan.AiTask("TELEPORT", "", "Invalid action", "x", 10),
                        new PlanService.AiPlan.AiTask("GRAMMAR_AREA", "not_an_area", "Invalid area", "x", 15),
                        new PlanService.AiPlan.AiTask("READING_PASSAGE", "7", "Passage with a bad variant", "x", 20))));
            }
            return new PlanService.AiPlan("Personalised focus.", days);
        });
        JsonNode plan = call(post("/api/plan/regenerate?ai=true"));
        assertThat(plan.get("source").asText()).isEqualTo("AI");
        assertThat(plan.get("focus").asText()).isEqualTo("Personalised focus.");
        List<String> titles = new ArrayList<>();
        List<String> variants = new ArrayList<>();
        int vocabDays = 0;
        for (JsonNode day : plan.get("days")) {
            boolean vocab = false;
            for (JsonNode t : day.get("tasks")) {
                titles.add(t.get("title").asText());
                if (t.get("action").asText().equals("READING_PASSAGE") && !t.get("variant").isNull()) {
                    variants.add(t.get("variant").asText());
                }
                vocab |= t.get("action").asText().equals("VOCAB_REVIEW");
            }
            vocabDays += vocab ? 1 : 0;
        }
        assertThat(titles).contains("Essay on remote work — focus on topic sentences").doesNotContain("Invalid action", "Invalid area");
        assertThat(variants).doesNotContain("7");
        assertThat(vocabDays).isGreaterThanOrEqualTo(6);
    }
}
