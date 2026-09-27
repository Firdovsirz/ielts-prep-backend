package com.ieltsprep.vocab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ieltsprep.common.ApiException;
import com.ieltsprep.common.Json;
import com.ieltsprep.content.Item;
import com.ieltsprep.content.ItemService;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.grading.GradingDispatcher;
import com.ieltsprep.vocab.VocabDtos.Added;
import com.ieltsprep.vocab.VocabDtos.BankSummary;
import com.ieltsprep.vocab.VocabDtos.BankView;
import com.ieltsprep.vocab.VocabDtos.CardView;
import com.ieltsprep.vocab.VocabDtos.Overview;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The vocabulary deck. Words arrive from Reading/Listening flags, Writing/Speaking vocabulary upgrades, topic word
 * banks and manual entry; they are scheduled with SM-2 and enriched (definition, examples, collocations) by Claude.
 */
@Service
public class VocabService implements VocabCapture {

    /** Function words and very common words that are never worth a flashcard. */
    static final Set<String> STOPWORDS = Set.of("the", "and", "for", "that", "this", "with", "have", "from", "they", "their", "there",
            "which", "would", "could", "about", "were", "been", "into", "than", "then", "them", "these", "those", "some", "such", "are",
            "was", "but", "not", "you", "your", "our", "his", "her", "she", "him", "its", "who", "what", "when", "where", "why", "how",
            "all", "any", "can", "will", "shall", "should", "may", "might", "must", "did", "does", "doing", "done", "has", "had", "having",
            "one", "two", "also", "just", "very", "more", "most", "much", "many", "other", "only", "over", "under", "after", "before",
            "because", "while", "well", "here", "out", "off", "own", "same", "too", "yes", "now", "get", "got", "let", "say", "said",
            "see", "way", "day", "time", "year", "people", "thing", "things", "good", "new", "first", "last", "long", "little", "make",
            "made", "know", "take", "come", "went", "going", "want", "look", "like", "use", "used", "okay", "right", "really");

    public enum CaptureResult { ADDED, EXISTS, IGNORED }

    private final VocabCardRepository cards;
    private final VocabReviewRepository reviews;
    private final AwlLookup awl;
    private final ItemService items;
    private final VocabEnricher enricher;
    private final GradingDispatcher dispatcher;
    private final Clock clock;

    public VocabService(VocabCardRepository cards, VocabReviewRepository reviews, AwlLookup awl, ItemService items, VocabEnricher enricher,
            GradingDispatcher dispatcher, Clock clock) {
        this.cards = cards;
        this.reviews = reviews;
        this.awl = awl;
        this.items = items;
        this.enricher = enricher;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void capture(String word, String contextSentence, String source, String sourceRef) {
        captureWord(word, contextSentence, source, sourceRef);
    }

    /** Adds a flagged word unless it is trivial (function word, contraction, too short) or already in the deck. */
    @Transactional
    public CaptureResult captureWord(String word, String contextSentence, String source, String sourceRef) {
        String w = normalise(word);
        if (w.length() < 3 || w.length() > 128 || w.contains("'") || w.contains("\u2019") || STOPWORDS.contains(w)) {
            return CaptureResult.IGNORED;
        }
        if (cards.findByWordIgnoreCase(w).isPresent()) {
            return CaptureResult.EXISTS;
        }
        VocabCard c = newCard(w, source);
        c.setContextSentence(cut(contextSentence, 2000));
        c.setSourceRef(cut(sourceRef, 255));
        cards.save(c);
        dispatcher.dispatch("enrich vocab", enricher::enrichPending);
        return CaptureResult.ADDED;
    }

    VocabCard newCard(String word, String source) {
        VocabCard c = new VocabCard();
        c.setWord(word);
        c.setSource(source);
        c.setAwlSublist(awl.sublist(word).orElse(null));
        c.setExamples("[]");
        c.setCollocations("[]");
        c.setDueAt(Instant.now(clock));
        c.setEnrichmentStatus("PENDING");
        c.setCreatedAt(Instant.now(clock));
        return c;
    }

    @Transactional
    public CardView add(VocabDtos.AddRequest req) {
        String w = normalise(req.word());
        if (w.isEmpty() || w.length() > 128) {
            throw ApiException.badRequest("Enter a word or short phrase (up to 128 characters)");
        }
        var existing = cards.findByWordIgnoreCase(w);
        if (existing.isPresent()) {
            if (!existing.get().isSuspended()) {
                throw ApiException.conflict("'" + w + "' is already in your deck");
            }
            existing.get().setSuspended(false);
            existing.get().setDueAt(Instant.now(clock));
            return view(existing.get());
        }
        VocabCard c = newCard(w, req.source() == null ? "MANUAL" : req.source());
        c.setContextSentence(cut(req.sentence(), 2000));
        cards.save(c);
        dispatcher.dispatch("enrich vocab", enricher::enrichPending);
        return view(c);
    }

    public Overview overview() {
        Instant now = Instant.now(clock);
        List<VocabCard> all = cards.findBySuspendedFalseOrderByCreatedAtDesc();
        Map<String, Long> bySource = all.stream().collect(Collectors.groupingBy(VocabCard::getSource, LinkedHashMap::new, Collectors.counting()));
        Instant nextDue = all.stream().map(VocabCard::getDueAt).filter(d -> d.isAfter(now)).min(Instant::compareTo).orElse(null);
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        return new Overview(all.size(), cards.countBySuspendedFalseAndDueAtLessThanEqual(now), cards.countBySuspendedFalseAndRepetitions(0),
                cards.countBySuspendedFalseAndIntervalDaysGreaterThanEqual(21), cards.countBySuspendedFalseAndAwlSublistIsNotNull(),
                reviews.countByReviewedAtGreaterThanEqual(startOfDay), nextDue, bySource);
    }

    public List<CardView> due(int limit) {
        return cards.findBySuspendedFalseAndDueAtLessThanEqualOrderByDueAtAsc(Instant.now(clock)).stream().limit(limit).map(this::view).toList();
    }

    public List<CardView> list() {
        return cards.findBySuspendedFalseOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    @Transactional
    public CardView review(long id, int grade) {
        VocabCard c = card(id);
        Instant now = Instant.now(clock);
        Sm2.Outcome o = Sm2.review(state(c), grade, now);
        c.setEaseFactor(o.state().easeFactor());
        c.setIntervalDays(o.state().intervalDays());
        c.setRepetitions(o.state().repetitions());
        c.setLapses(o.state().lapses());
        c.setDueAt(o.dueAt());
        c.setLastReviewedAt(now);
        VocabReview r = new VocabReview();
        r.setCardId(id);
        r.setGrade(grade);
        r.setIntervalDaysAfter(o.state().intervalDays());
        r.setReviewedAt(now);
        reviews.save(r);
        return view(c);
    }

    @Transactional
    public CardView edit(long id, VocabDtos.EditRequest req) {
        VocabCard c = card(id);
        if (req.definition() != null) {
            c.setDefinition(cut(req.definition(), 2000));
        }
        if (req.partOfSpeech() != null) {
            c.setPartOfSpeech(cut(req.partOfSpeech(), 32));
        }
        if (req.examples() != null) {
            c.setExamples(Json.write(req.examples()));
        }
        if (req.collocations() != null) {
            c.setCollocations(Json.write(req.collocations()));
        }
        c.setEnrichmentStatus("DONE");
        return view(c);
    }

    @Transactional
    public void remove(long id) {
        card(id).setSuspended(true);
    }

    public List<BankSummary> banks() {
        Set<String> deck = deckWords();
        return items.list(TaskType.VOCAB_WORD_BANK).stream().map(i -> {
            WordBank b = i.contentAs(WordBank.class);
            int in = (int) b.words().stream().filter(e -> deck.contains(normalise(e.word()))).count();
            return new BankSummary(i.getId(), b.topic(), b.words().size(), in, i.getOrigin().name());
        }).toList();
    }

    public BankView bank(long itemId) {
        Item i = items.get(itemId);
        if (i.getTaskType() != TaskType.VOCAB_WORD_BANK) {
            throw ApiException.badRequest("Not a word bank");
        }
        WordBank b = i.contentAs(WordBank.class);
        Set<String> deck = deckWords();
        return new BankView(itemId, b.topic(), b.words(),
                b.words().stream().map(e -> normalise(e.word())).filter(deck::contains).toList());
    }

    /** Adds a bank's words as ready-made cards (already enriched from the bank). */
    @Transactional
    public Added addBank(long itemId) {
        WordBank b = items.get(itemId).contentAs(WordBank.class);
        int added = 0;
        int skipped = 0;
        for (WordBank.Entry e : b.words()) {
            String w = normalise(e.word());
            if (cards.findByWordIgnoreCase(w).isPresent()) {
                skipped++;
                continue;
            }
            VocabCard c = newCard(w, "WORD_BANK");
            c.setPartOfSpeech(cut(e.partOfSpeech(), 32));
            c.setDefinition(e.definition());
            c.setExamples(Json.write(List.of(e.example())));
            c.setCollocations(Json.write(e.collocations()));
            c.setCefr(e.cefr());
            c.setTopic(b.topic());
            c.setSourceRef("bank " + itemId);
            c.setEnrichmentStatus("DONE");
            cards.save(c);
            added++;
        }
        return new Added(added, skipped);
    }

    private Set<String> deckWords() {
        return cards.findAll().stream().map(c -> c.getWord().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    private VocabCard card(long id) {
        return cards.findById(id).orElseThrow(() -> ApiException.notFound("Card " + id));
    }

    static Sm2.State state(VocabCard c) {
        return new Sm2.State(c.getEaseFactor(), c.getIntervalDays(), c.getRepetitions(), c.getLapses());
    }

    CardView view(VocabCard c) {
        Instant now = Instant.now(clock);
        Map<String, String> previews = new LinkedHashMap<>();
        previews.put("again", Sm2.preview(state(c), 1, now));
        previews.put("hard", Sm2.preview(state(c), 3, now));
        previews.put("good", Sm2.preview(state(c), 4, now));
        previews.put("easy", Sm2.preview(state(c), 5, now));
        return new CardView(c.getId(), c.getWord(), c.getPartOfSpeech(), c.getDefinition(), list(c.getExamples()), list(c.getCollocations()),
                c.getAwlSublist(), c.getCefr(), c.getTopic(), c.getSource(), c.getContextSentence(), c.getIntervalDays(), c.getRepetitions(),
                c.getLapses(), c.getEaseFactor(), c.getDueAt(), c.getLastReviewedAt(), c.getEnrichmentStatus(), previews);
    }

    private static List<String> list(String json) {
        List<String> l = Json.read(json, new TypeReference<List<String>>() {});
        return l == null ? List.of() : l;
    }

    static String normalise(String word) {
        return word == null ? "" : word.trim().toLowerCase(Locale.ROOT).replaceAll("^[^\\p{L}]+|[^\\p{L}]+$", "").replaceAll("\\s+", " ");
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
