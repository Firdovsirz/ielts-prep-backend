package com.ieltsprep.grammar;

import com.ieltsprep.attempt.AttemptRepository;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.ApiException;
import com.ieltsprep.common.Sentences;
import com.ieltsprep.content.Item;
import com.ieltsprep.content.ItemFactory;
import com.ieltsprep.content.ItemOrigin;
import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.content.VerificationStatus;
import com.ieltsprep.errorlog.ErrorEntry;
import com.ieltsprep.errorlog.ErrorLogService;
import com.ieltsprep.errorlog.ErrorsRecordedEvent;
import com.ieltsprep.generation.GenerationRequest;
import com.ieltsprep.generation.GenerationService;
import com.ieltsprep.grading.GradingDispatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Error-driven practice: after each graded Writing/Speaking piece, drills are prepared in the background for the
 * candidate's grammar error subtypes — their own sentence to correct plus three fresh items on the same rule. With
 * no API key, drills are assembled from the error log and verified seed exercises instead.
 */
@Service
public class ErrorDrillService {

    private static final Logger log = LoggerFactory.getLogger(ErrorDrillService.class);
    static final int MAX_DRILLS_PER_PIECE = 4;

    private final ErrorLogService errorLog;
    private final ItemRepository items;
    private final ItemFactory factory;
    private final AttemptRepository attempts;
    private final GrammarTaxonomy taxonomy;
    private final GenerationService generation;
    private final ClaudeService claude;
    private final GradingDispatcher dispatcher;
    private final TransactionTemplate tx;

    public ErrorDrillService(ErrorLogService errorLog, ItemRepository items, ItemFactory factory, AttemptRepository attempts,
            GrammarTaxonomy taxonomy, GenerationService generation, ClaudeService claude, GradingDispatcher dispatcher,
            TransactionTemplate tx) {
        this.errorLog = errorLog;
        this.items = items;
        this.factory = factory;
        this.attempts = attempts;
        this.taxonomy = taxonomy;
        this.generation = generation;
        this.claude = claude;
        this.dispatcher = dispatcher;
        this.tx = tx;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onErrorsRecorded(ErrorsRecordedEvent event) {
        if (event.grammarSubtypes().isEmpty()) {
            return;
        }
        dispatcher.dispatch("prepare drills", () -> prepare(event.grammarSubtypes()));
    }

    /** Ensures an unused drill exists for the highest-ranked of these subtypes. */
    public void prepare(java.util.Set<String> subtypes) {
        List<String> ranked = errorLog.summary("grammar").stream().map(s -> s.subtype()).filter(subtypes::contains)
                .limit(MAX_DRILLS_PER_PIECE).toList();
        for (String subtype : ranked) {
            if (unserved(subtype).isEmpty()) {
                try {
                    create(subtype, true);
                } catch (Exception e) {
                    log.warn("Could not prepare drill for {}: {}", subtype, e.getMessage());
                }
            }
        }
    }

    /** A drill ready to start now: an unused prepared drill, otherwise one assembled instantly (never waits on Claude). */
    public Item drillFor(String subtype) {
        return unserved(subtype).orElseGet(() -> assemble(subtype));
    }

    Item create(String subtype, boolean background) {
        if (claude.isAvailable()) {
            GenerationService.Outcome o = generation.generate(
                    new GenerationRequest(TaskType.GRAMMAR_ERROR_DRILL, subtype, background, Map.of("subtype", subtype), null), 2);
            if (o.verified()) {
                return o.item();
            }
        }
        return assemble(subtype);
    }

    private Optional<Item> unserved(String subtype) {
        return items.findByTaskTypeAndVariantAndVerificationStatusOrderByIdAsc(TaskType.GRAMMAR_ERROR_DRILL, subtype,
                VerificationStatus.VERIFIED).stream().filter(i -> i.getTimesServed() == 0).findFirst();
    }

    /** Offline drill: the candidate's latest sentence with this error + three closed items from the area's exercise sets. */
    Item assemble(String subtype) {
        List<ErrorEntry> rows = errorLog.bySubtype(subtype);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Errors of subtype " + subtype);
        }
        ErrorEntry latest = rows.getFirst();
        String area = latest.getGrammarArea() != null ? latest.getGrammarArea() : taxonomy.areaForSubtype(subtype).orElse("AGREEMENT_ARTICLES");
        String text = GrammarService.attemptText(attempts.findById(latest.getAttemptId()).orElse(null));
        String own = Sentences.containing(text, latest.getOriginal());
        String corrected = latest.getCorrection() == null ? own : own.replace(latest.getOriginal(), latest.getCorrection());
        List<GrammarContent.ExerciseItem> fresh = new ArrayList<>();
        List<Item> sets = items.findByTaskTypeAndVariantAndVerificationStatusOrderByIdAsc(TaskType.GRAMMAR_EXERCISE, area,
                VerificationStatus.VERIFIED);
        int offset = latest.getDrillCount() * 3;
        List<GrammarContent.ExerciseItem> pool = sets.stream().map(i -> i.contentAs(GrammarContent.ExerciseSet.class))
                .flatMap(s -> s.items().stream()).filter(i -> !"AI".equals(i.checkMode())).toList();
        for (int k = 0; k < Math.min(3, pool.size()); k++) {
            GrammarContent.ExerciseItem src = pool.get((offset + k) % pool.size());
            fresh.add(new GrammarContent.ExerciseItem(String.valueOf(k + 1), src.prompt(), src.options(), src.acceptedAnswers(),
                    src.modelAnswer(), src.checkMode(), src.explanation()));
        }
        GrammarTaxonomy.Area a = taxonomy.require(area);
        GrammarContent.ErrorDrill drill = new GrammarContent.ErrorDrill(subtype, area,
                (latest.getExplanation() == null ? "" : latest.getExplanation() + " ") + "Focus: " + a.focus(), own,
                List.of(corrected), latest.getExplanation() == null ? "" : latest.getExplanation(), fresh);
        return tx.execute(s -> {
            Item item = factory.create(TaskType.GRAMMAR_ERROR_DRILL, drill, null, "Original content", ItemOrigin.GENERATED);
            item.setVerificationStatus(VerificationStatus.VERIFIED);
            item.setVerificationNotes("Assembled offline from the error log and verified exercise sets.");
            return items.save(item);
        });
    }
}
