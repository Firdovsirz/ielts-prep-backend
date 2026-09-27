package com.ieltsprep.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class VocabFlowTest {

    @Autowired MockMvc mvc;
    @Autowired VocabCardRepository cards;
    @MockitoBean ClaudeService claude;
    String auth;

    @BeforeEach
    void login() throws Exception {
        auth = TestAuth.bearer(mvc);
    }

    JsonNode call(MockHttpServletRequestBuilder req, int expected) throws Exception {
        String body = mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : Json.tree(body);
    }

    @Test
    void addedWordsAreEnrichedScheduledAndReviewedWithSm2() throws Exception {
        when(claude.isAvailable()).thenReturn(true);
        when(claude.call(any())).thenAnswer(inv -> new VocabEnricher.Enriched(List.of(new VocabEnricher.Enriched.Card("mitigate", "verb",
                "to make something less harmful", List.of("Governments must mitigate the effects of drought.", "Trees mitigate heat."),
                List.of("mitigate the impact", "mitigate risk"), "C1", "environment"))));

        JsonNode card = call(post("/api/vocab/cards").content("{\"word\":\"  Mitigate. \",\"sentence\":\"to mitigate flooding\"}"), 200);
        long id = card.get("id").asLong();
        assertThat(card.get("word").asText()).isEqualTo("mitigate");
        assertThat(card.get("previews").get("again").asText()).isEqualTo("10 min");
        assertThat(card.get("previews").get("good").asText()).isEqualTo("2 d");

        VocabCard enriched = cards.findById(id).orElseThrow();
        assertThat(enriched.getEnrichmentStatus()).isEqualTo("DONE");
        assertThat(enriched.getDefinition()).contains("less harmful");
        assertThat(enriched.getCefr()).isEqualTo("C1");

        call(post("/api/vocab/cards").content("{\"word\":\"mitigate\"}"), 409);

        JsonNode due = call(get("/api/vocab/due"), 200);
        assertThat(due.findValuesAsText("word")).contains("mitigate");

        JsonNode reviewed = call(post("/api/vocab/cards/" + id + "/review").content("{\"grade\":4}"), 200);
        assertThat(reviewed.get("intervalDays").asInt()).isEqualTo(2);
        assertThat(reviewed.get("repetitions").asInt()).isEqualTo(1);
        assertThat(call(get("/api/vocab/due"), 200).findValuesAsText("word")).doesNotContain("mitigate");

        JsonNode overview = call(get("/api/vocab/overview"), 200);
        assertThat(overview.get("reviewedToday").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(overview.get("total").asInt()).isGreaterThanOrEqualTo(1);

        call(delete("/api/vocab/cards/" + id), 200);
        assertThat(call(get("/api/vocab/cards"), 200).findValuesAsText("word")).doesNotContain("mitigate");
        JsonNode restored = call(post("/api/vocab/cards").content("{\"word\":\"mitigate\"}"), 200);
        assertThat(restored.get("id").asLong()).isEqualTo(id);
    }

    @Test
    void capturedWordsGetAwlTagsAndSkipStopwordsAndDuplicates() throws Exception {
        when(claude.isAvailable()).thenReturn(false);
        assertThat(call(post("/api/vocab/capture").content("{\"word\":\"Analysis,\",\"sentence\":\"a detailed analysis\",\"source\":\"LISTENING_FLAG\"}"), 200)
                .get("result").asText()).isEqualTo("ADDED");
        assertThat(call(post("/api/vocab/capture").content("{\"word\":\"analysis\"}"), 200).get("result").asText()).isEqualTo("EXISTS");
        assertThat(call(post("/api/vocab/capture").content("{\"word\":\"the\"}"), 200).get("result").asText()).isEqualTo("IGNORED");
        assertThat(call(post("/api/vocab/capture").content("{\"word\":\"I'm\"}"), 200).get("result").asText()).isEqualTo("IGNORED");

        VocabCard c = cards.findByWordIgnoreCase("analysis").orElseThrow();
        assertThat(c.getAwlSublist()).isEqualTo(1);
        assertThat(c.getSource()).isEqualTo("LISTENING_FLAG");
        assertThat(c.getEnrichmentStatus()).isEqualTo("PENDING");
        assertThat(cards.findByWordIgnoreCase("the")).isEmpty();
    }

    @Test
    void seededWordBanksCanBeAddedToTheDeckOnce() throws Exception {
        JsonNode banks = call(get("/api/vocab/banks"), 200);
        assertThat(banks.size()).isGreaterThanOrEqualTo(3);
        long itemId = banks.get(0).get("itemId").asLong();

        JsonNode bank = call(get("/api/vocab/banks/" + itemId), 200);
        int words = bank.get("words").size();
        assertThat(words).isGreaterThanOrEqualTo(10);

        JsonNode added = call(post("/api/vocab/banks/" + itemId + "/add"), 200);
        assertThat(added.get("added").asInt() + added.get("skipped").asInt()).isEqualTo(words);
        JsonNode again = call(post("/api/vocab/banks/" + itemId + "/add"), 200);
        assertThat(again.get("added").asInt()).isZero();
        assertThat(call(get("/api/vocab/banks/" + itemId), 200).get("inDeck").size()).isEqualTo(words);

        String first = bank.get("words").get(0).get("word").asText();
        VocabCard c = cards.findByWordIgnoreCase(VocabService.normalise(first)).orElseThrow();
        assertThat(c.getEnrichmentStatus()).isEqualTo("DONE");
        assertThat(c.getSource()).isEqualTo("WORD_BANK");
    }

    @Test
    void generatingABankNeedsClaude() throws Exception {
        when(claude.isAvailable()).thenReturn(false);
        call(post("/api/vocab/banks/generate/environment"), 503);
    }
}
