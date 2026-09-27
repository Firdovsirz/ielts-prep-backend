package com.ieltsprep.errorlog;

import com.ieltsprep.attempt.Attempt;
import com.ieltsprep.attempt.AttemptRepository;
import com.ieltsprep.attempt.AttemptStatus;
import com.ieltsprep.content.Skill;
import com.ieltsprep.grading.GradingModels.TaggedError;
import com.ieltsprep.grammar.GrammarTaxonomy;
import com.ieltsprep.settings.SettingsService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The error log: every error the Writing/Speaking graders tag is stored with its taxonomy subtype and grammar area,
 * grouped/ranked for the dashboard and the Grammar module, and auto-resolved when it stops appearing.
 */
@Service
public class ErrorLogService {

    public static final int TREND_WINDOW = 3;

    private final ErrorEntryRepository errors;
    private final AttemptRepository attempts;
    private final GrammarTaxonomy taxonomy;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ErrorLogService(ErrorEntryRepository errors, AttemptRepository attempts, GrammarTaxonomy taxonomy,
            SettingsService settings, ApplicationEventPublisher events, Clock clock) {
        this.errors = errors;
        this.attempts = attempts;
        this.taxonomy = taxonomy;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public List<ErrorEntry> record(Attempt attempt, List<TaggedError> tagged) {
        Instant now = Instant.now(clock);
        List<ErrorEntry> saved = tagged.stream()
                .filter(e -> e.original() != null && !e.original().isBlank())
                .map(e -> {
                    ErrorEntry entry = new ErrorEntry();
                    entry.setAttemptId(attempt.getId());
                    entry.setModule(attempt.getModule());
                    String type = e.type() == null ? "grammar" : e.type().toLowerCase(Locale.ROOT);
                    String subtype = GrammarTaxonomy.normalise(e.subtype());
                    entry.setType(type);
                    entry.setSubtype(subtype.isBlank() ? "other" : subtype);
                    entry.setGrammarArea("grammar".equals(type) ? taxonomy.areaForSubtype(subtype).orElse(null) : null);
                    entry.setOriginal(cut(e.original(), 4000));
                    entry.setCorrection(cut(e.correction(), 4000));
                    entry.setExplanation(cut(e.explanation(), 4000));
                    entry.setCreatedAt(now);
                    return errors.save(entry);
                })
                .toList();
        refreshResolution();
        Set<String> grammar = saved.stream().filter(e -> e.getGrammarArea() != null).map(ErrorEntry::getSubtype).collect(Collectors.toSet());
        events.publishEvent(new ErrorsRecordedEvent(attempt.getId(), grammar));
        return saved;
    }

    /** Marks subtypes resolved once they are absent from the last N graded pieces (N from Settings). */
    @Transactional
    public Set<String> refreshResolution() {
        List<ErrorAnalytics.Row> rows = rows();
        Set<String> resolved = ErrorAnalytics.newlyResolved(rows, gradedNewestFirst(), settings.get().getResolvedAfterPieces());
        Instant now = Instant.now(clock);
        for (String subtype : resolved) {
            errors.findBySubtypeAndResolvedAtIsNull(subtype).forEach(e -> e.setResolvedAt(now));
        }
        return resolved;
    }

    public List<ErrorAnalytics.SubtypeStats> summary() {
        return ErrorAnalytics.group(rows(), gradedNewestFirst(), Instant.now(clock), TREND_WINDOW);
    }

    public List<ErrorAnalytics.SubtypeStats> summary(String type) {
        return summary().stream().filter(s -> s.type().equals(type)).toList();
    }

    public List<ErrorEntry> forAttempt(long attemptId) {
        return errors.findByAttemptId(attemptId);
    }

    public List<ErrorEntry> bySubtype(String subtype) {
        return errors.findBySubtypeOrderByCreatedAtDesc(subtype);
    }

    public List<ErrorEntry> all() {
        return errors.findAllByOrderByCreatedAtDesc();
    }

    /** Graded Writing and Speaking pieces, newest first. */
    public List<Long> gradedNewestFirst() {
        return attempts.findByModuleInAndStatusOrderBySubmittedAtDesc(List.of(Skill.WRITING, Skill.SPEAKING), AttemptStatus.GRADED)
                .stream().map(Attempt::getId).toList();
    }

    private List<ErrorAnalytics.Row> rows() {
        return errors.findAllByOrderByCreatedAtDesc().stream()
                .map(e -> new ErrorAnalytics.Row(e.getId(), e.getAttemptId(), e.getType(), e.getSubtype(), e.getGrammarArea(),
                        e.getCreatedAt(), e.getResolvedAt(), e.getOriginal(), e.getCorrection()))
                .toList();
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
