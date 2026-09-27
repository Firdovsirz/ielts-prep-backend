package com.ieltsprep.vocab;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class VocabDtos {

    private VocabDtos() {}

    @Schema(name = "VocabCardView")
    public record CardView(long id, String word, @Schema(nullable = true) String partOfSpeech, @Schema(nullable = true) String definition,
            List<String> examples, List<String> collocations, @Schema(nullable = true) Integer awlSublist,
            @Schema(nullable = true) String cefr, @Schema(nullable = true) String topic, String source,
            @Schema(nullable = true) String contextSentence, int intervalDays, int repetitions, int lapses, double easeFactor,
            Instant dueAt, @Schema(nullable = true) Instant lastReviewedAt, String enrichmentStatus, Map<String, String> previews) {}

    @Schema(name = "VocabOverview")
    public record Overview(long total, long due, long fresh, long learned, long awl, long reviewedToday,
            @Schema(nullable = true) Instant nextDue, Map<String, Long> bySource) {}

    @Schema(name = "VocabReviewRequest")
    public record ReviewRequest(int grade) {}

    @Schema(name = "VocabAddRequest")
    public record AddRequest(@NotBlank String word, String sentence, String source) {}

    @Schema(name = "VocabEditRequest")
    public record EditRequest(String definition, String partOfSpeech, List<String> examples, List<String> collocations) {}

    @Schema(name = "WordBankSummary")
    public record BankSummary(long itemId, String topic, int words, int inDeck, String origin) {}

    @Schema(name = "WordBankView")
    public record BankView(long itemId, String topic, List<WordBank.Entry> words, List<String> inDeck) {}

    @Schema(name = "VocabCaptured")
    public record Captured(String word, VocabService.CaptureResult result) {}

    @Schema(name = "VocabAddedResult")
    public record Added(int added, int skipped) {}
}
