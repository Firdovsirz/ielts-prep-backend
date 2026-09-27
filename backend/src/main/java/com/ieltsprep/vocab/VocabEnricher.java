package com.ieltsprep.vocab;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ieltsprep.claude.ClaudeCall;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Fills in PENDING cards with the fast model (vocab-enrich), in batches of up to 20 words. */
@Component
public class VocabEnricher {

    private static final Logger log = LoggerFactory.getLogger(VocabEnricher.class);
    static final int BATCH = 20;

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Enriched(List<Card> cards) {
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        record Card(String word, String partOfSpeech, String definition, List<String> examples, List<String> collocations, String cefr,
                String topic) {}
    }

    private final VocabCardRepository cards;
    private final ClaudeService claude;
    private final TransactionTemplate tx;
    private final AtomicBoolean running = new AtomicBoolean();

    public VocabEnricher(VocabCardRepository cards, ClaudeService claude, TransactionTemplate tx) {
        this.cards = cards;
        this.claude = claude;
        this.tx = tx;
    }

    @Scheduled(initialDelay = 1, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void scheduled() {
        enrichPending();
    }

    public void enrichPending() {
        if (!claude.isAvailable() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            for (int round = 0; round < 5 && enrichBatch(); round++) {
                // keep going while full batches are being processed
            }
        } catch (Exception e) {
            log.info("Vocabulary enrichment skipped: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /** Enriches one batch; returns true when there may be more pending cards. */
    boolean enrichBatch() {
        List<VocabCard> pending = cards.findByEnrichmentStatusOrderByIdAsc("PENDING").stream().limit(BATCH).toList();
        if (pending.isEmpty()) {
            return false;
        }
        String words = pending.stream()
                .map(c -> "- " + c.getWord() + (c.getContextSentence() == null || c.getContextSentence().isBlank() ? ""
                        : " (context: \"" + c.getContextSentence() + "\")"))
                .collect(Collectors.joining("\n"));
        Enriched result = claude.call(ClaudeCall.of("vocab-enrich", Map.of("words", words), Enriched.class).inBackground()
                .ref("vocab enrich " + pending.size()));
        Map<String, Enriched.Card> byWord = result.cards().stream()
                .collect(Collectors.toMap(c -> c.word().toLowerCase(), c -> c, (a, b) -> a));
        tx.executeWithoutResult(s -> pending.forEach(p -> {
            VocabCard c = cards.findById(p.getId()).orElseThrow();
            Enriched.Card e = byWord.get(c.getWord().toLowerCase());
            if (e == null) {
                c.setEnrichmentStatus("FAILED");
                return;
            }
            c.setPartOfSpeech(cut(e.partOfSpeech(), 32));
            c.setDefinition(e.definition());
            c.setExamples(Json.write(e.examples()));
            c.setCollocations(Json.write(e.collocations()));
            c.setCefr(cut(e.cefr(), 8));
            c.setTopic(cut(e.topic(), 64));
            c.setEnrichmentStatus("DONE");
        }));
        return pending.size() == BATCH;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
