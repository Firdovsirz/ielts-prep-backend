package com.ieltsprep.writing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.claude.ClaudeException;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import com.ieltsprep.errorlog.ErrorEntryRepository;
import com.ieltsprep.grading.GradingModels.CriterionBand;
import com.ieltsprep.grading.GradingModels.TaggedError;
import com.ieltsprep.grading.GradingModels.VocabUpgrade;
import com.ieltsprep.grading.GradingModels.WritingGrade;
import com.ieltsprep.support.IntegrationTest;
import com.ieltsprep.support.TestAuth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class WritingFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ErrorEntryRepository errors;
    @MockitoBean ClaudeService claude;
    String auth;

    static final String ESSAY = "Some people believe that working four days is better. In my opinion, this have many benefit "
            + "for employees and also for companies. Firstly, the workers is more rested and they are more productive.";

    static WritingGrade grade() {
        return new WritingGrade(
                List.of(new CriterionBand("TASK_RESPONSE", 6, "Addresses the prompt; \"many benefit\" is underdeveloped.", List.of(), List.of()),
                        new CriterionBand("COHERENCE_COHESION", 7, "Logical.", List.of(), List.of()),
                        new CriterionBand("LEXICAL_RESOURCE", 6, "Adequate.", List.of(), List.of()),
                        new CriterionBand("GRAMMATICAL_RANGE_ACCURACY", 6, "Frequent agreement errors.", List.of(), List.of())),
                List.of(new TaggedError("grammar", "Subject-Verb Agreement", "this have", "this has", "Singular subject."),
                        new TaggedError("grammar", "plural_form", "many benefit", "many benefits", "Countable plural."),
                        new TaggedError("vocabulary", "word_choice", "better", "more beneficial", "Precision.")),
                List.of("Develop each idea with an example", "Fix agreement", "Vary linkers"),
                List.of(new VocabUpgrade("many benefit", "numerous advantages", "This has numerous advantages for employees.")),
                "Under length.", "Solid band 6.", "A model answer…");
    }

    @BeforeEach
    void login() throws Exception {
        auth = TestAuth.bearer(mvc);
        when(claude.isAvailable()).thenReturn(true);
    }

    private long startTask2() throws Exception {
        JsonNode s = Json.tree(mvc.perform(post("/api/writing/sessions").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"PRACTICE\",\"scope\":\"TASK2\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(s.at("/tasks/0/task").asInt()).isEqualTo(2);
        assertThat(s.at("/tasks/0/task2/prompt").asText()).contains("250 words");
        assertThat(s.get("timeLimitSeconds").asInt()).isEqualTo(2400);
        return s.get("sessionId").asLong();
    }

    private JsonNode submit(long id, String text) throws Exception {
        long itemId = Json.tree(mvc.perform(get("/api/writing/sessions/" + id).header("Authorization", auth))
                .andReturn().getResponse().getContentAsString()).at("/tasks/0/itemId").asLong();
        JsonNode submitted = Json.tree(mvc.perform(post("/api/writing/sessions/" + id + "/submit").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(Json.write(Map.of("responses", List.of(Map.of("itemId", itemId, "text", text, "secondsSpent", 1800)),
                        "timeUsedSeconds", 1850))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        // grading runs after the submit transaction commits (inline in tests); the client polls the result
        assertThat(submitted.get("status").asText()).isIn("GRADING", "FAILED");
        return result(id);
    }

    private JsonNode result(long id) throws Exception {
        return Json.tree(mvc.perform(get("/api/writing/sessions/" + id + "/result").header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void submittedEssayIsGradedAndErrorsAreLogged() throws Exception {
        when(claude.call(any())).thenAnswer(inv -> grade());
        long before = errors.count();

        JsonNode result = submit(startTask2(), ESSAY);

        assertThat(result.get("status").asText()).isEqualTo("GRADED");
        JsonNode attempt = result.at("/attempts/0");
        assertThat(attempt.get("band").asDouble()).isEqualTo(6.0); // (6+7+6+6)/4 = 6.25 → rounded down to 6.0
        assertThat(attempt.at("/criteriaBands/COHERENCE_COHESION").asInt()).isEqualTo(7);
        assertThat(attempt.get("wordCount").asInt()).isEqualTo(33);
        assertThat(attempt.at("/grade/model_answer").asText()).isNotBlank();
        assertThat(result.get("writingBand").asDouble()).isEqualTo(6.0);

        assertThat(errors.count()).isEqualTo(before + 3);
        var agreement = errors.findAll().stream().filter(e -> e.getOriginal().equals("this have")).findFirst().orElseThrow();
        assertThat(agreement.getSubtype()).isEqualTo("subject_verb_agreement"); // normalised
        assertThat(agreement.getGrammarArea()).isEqualTo("AGREEMENT_ARTICLES");
        assertThat(errors.findAll().stream().filter(e -> e.getType().equals("vocabulary")).findFirst().orElseThrow().getGrammarArea()).isNull();

        JsonNode summary = Json.tree(mvc.perform(get("/api/errors/summary").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString());
        assertThat(summary.findValuesAsText("subtype")).contains("subject_verb_agreement", "plural_form");
    }

    @Test
    void failedGradingCanBeRetried() throws Exception {
        when(claude.call(any())).thenThrow(new ClaudeException("CLAUDE_UNAVAILABLE", "overloaded"));
        JsonNode failed = submit(startTask2(), ESSAY);
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.at("/attempts/0/error").asText()).contains("overloaded");

        org.mockito.Mockito.doAnswer(inv -> grade()).when(claude).call(any());
        long attemptId = failed.at("/attempts/0/attemptId").asLong();
        mvc.perform(post("/api/writing/attempts/" + attemptId + "/regrade").header("Authorization", auth)).andExpect(status().isOk());
        long sessionId = failed.get("sessionId").asLong();
        assertThat(result(sessionId).get("status").asText()).isEqualTo("GRADED");
    }

    @Test
    void blankResponseIsRecordedButNotSentForGrading() throws Exception {
        JsonNode result = submit(startTask2(), "   ");
        assertThat(result.at("/attempts/0/status").asText()).isEqualTo("GRADING_FAILED");
        assertThat(result.at("/attempts/0/error").asText()).contains("No response");
    }
}
