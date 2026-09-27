package com.ieltsprep.grammar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.content.VerificationStatus;
import com.ieltsprep.support.IntegrationTest;
import com.ieltsprep.support.TestAuth;
import com.ieltsprep.support.Fixtures;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class GrammarFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ItemRepository items;
    @MockitoBean ClaudeService claude;
    String auth;

    @BeforeEach
    void login() throws Exception {
        auth = TestAuth.bearer(mvc);
    }

    JsonNode post(String url, Object body) throws Exception {
        return Json.tree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(Json.write(body)))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void adaptiveDiagnosticRunsFortyQuestionsAndStoresProficiency() throws Exception {
        Map<String, String> key = new HashMap<>();
        items.findByTaskTypeAndVerificationStatusOrderByIdAsc(TaskType.GRAMMAR_DIAGNOSTIC, VerificationStatus.VERIFIED)
                .forEach(i -> {
                    GrammarContent.DiagnosticQuestion q = i.contentAs(GrammarContent.DiagnosticQuestion.class);
                    key.put(q.id(), q.answer());
                });
        JsonNode view = post("/api/grammar/diagnostic", Map.of());
        long id = view.get("diagnosticId").asLong();
        int n = 0;
        while (view.get("question") != null && !view.get("question").isNull()) {
            String qid = view.at("/question/id").asText();
            // know everything except conditionals
            String answer = qid.startsWith("CONDITIONALS") ? "Z" : key.get(qid);
            JsonNode res = post("/api/grammar/diagnostic/" + id + "/answer", Map.of("questionId", qid, "answer", answer));
            view = res.get("next");
            n++;
        }
        assertThat(n).isEqualTo(40);
        assertThat(view.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(view.at("/scores/TENSE_ASPECT").asDouble()).isEqualTo(100.0);
        assertThat(view.at("/scores/CONDITIONALS").asDouble()).isEqualTo(0.0);

        JsonNode overview = Json.tree(mvc.perform(get("/api/grammar").header("Authorization", auth)).andReturn().getResponse().getContentAsString());
        assertThat(overview.get("diagnosticDone").asBoolean()).isTrue();
        JsonNode conditionals = null;
        for (JsonNode a : overview.get("areas")) {
            if (a.get("key").asText().equals("CONDITIONALS")) {
                conditionals = a;
            }
        }
        assertThat(conditionals.get("recommended").asBoolean()).isTrue();
    }

    @Test
    void exerciseSetIsMarkedExactlyAndOpenItemsAreSelfAssessedWithoutAnApiKey() throws Exception {
        when(claude.isAvailable()).thenReturn(false);
        var set = items.findByTaskTypeAndVariantAndVerificationStatusOrderByIdAsc(TaskType.GRAMMAR_EXERCISE, "CONDITIONALS",
                VerificationStatus.VERIFIED).stream().filter(i -> i.getQuestionTypes().equals("SENTENCE_TRANSFORMATION")).findFirst().orElseThrow();
        GrammarContent.ExerciseSet content = set.contentAs(GrammarContent.ExerciseSet.class);

        JsonNode session = post("/api/grammar/exercises/" + set.getId() + "/start", Map.of());
        assertThat(session.at("/items/0/accepted_answers")).isEmpty();
        long id = session.get("sessionId").asLong();

        Map<String, String> answers = new HashMap<>();
        answers.put("1", content.items().get(0).acceptedAnswers().getFirst());       // matches the key
        answers.put("2", "Something different that the key does not list.");        // needs checking → SELF
        JsonNode result = post("/api/grammar/sessions/" + id + "/submit", Map.of("answers", answers));
        assertThat(result.at("/items/0/correct").asBoolean()).isTrue();
        assertThat(result.at("/items/1/method").asText()).isEqualTo("SELF");
        assertThat(result.get("pending").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode assessed = post("/api/grammar/sessions/" + id + "/self-assess", Map.of("verdicts", Map.of("2", true)));
        assertThat(assessed.at("/items/1/correct").asBoolean()).isTrue();
        assertThat(assessed.get("score").asInt()).isEqualTo(2);
    }

    @Test
    void errorsFromGradedWritingBecomeADrillWithTheCandidatesOwnSentence() throws Exception {
        when(claude.isAvailable()).thenReturn(true);
        when(claude.call(any())).thenAnswer(inv -> Fixtures.writingGrade());
        // write + grade an essay so the error log has subject_verb_agreement errors
        JsonNode s = post("/api/writing/sessions", Map.of("mode", "PRACTICE", "scope", "TASK2"));
        long itemId = s.at("/tasks/0/itemId").asLong();
        post("/api/writing/sessions/" + s.get("sessionId").asLong() + "/submit",
                Map.of("responses", List.of(Map.of("itemId", itemId, "text", Fixtures.ESSAY))));

        // no prepared drill in the mock world → assembled instantly from the error log + seed exercises
        when(claude.isAvailable()).thenReturn(false);
        JsonNode overview = Json.tree(mvc.perform(get("/api/grammar").header("Authorization", auth)).andReturn().getResponse().getContentAsString());
        assertThat(overview.get("errorPatterns").findValuesAsText("subtype")).contains("subject_verb_agreement");

        JsonNode drill = post("/api/grammar/error-practice/subject_verb_agreement/start", Map.of());
        assertThat(drill.get("ownSentence").asText()).contains("this have many benefit");
        assertThat(drill.get("items").size()).isEqualTo(4);
        JsonNode result = post("/api/grammar/sessions/" + drill.get("sessionId").asLong() + "/submit",
                Map.of("answers", Map.of("own", "In my opinion, this has many benefit for employees and also for companies.")));
        assertThat(result.get("kind").asText()).isEqualTo("GRAMMAR_ERROR_DRILL");
        assertThat(result.get("rule").asText()).isNotBlank();
    }
}
