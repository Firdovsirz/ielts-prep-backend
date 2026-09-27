package com.ieltsprep.reading;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ieltsprep.attempt.Attempt;
import com.ieltsprep.attempt.AttemptRepository;
import com.ieltsprep.attempt.AttemptStatus;
import com.ieltsprep.band.BandCalculator;
import com.ieltsprep.common.ApiException;
import com.ieltsprep.common.Json;
import com.ieltsprep.content.Item;
import com.ieltsprep.content.ItemService;
import com.ieltsprep.content.Skill;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.content.model.Questions;
import com.ieltsprep.marking.QuestionMarker;
import com.ieltsprep.marking.QuestionMarker.QuestionResult;
import com.ieltsprep.reading.ReadingDtos.PassageResult;
import com.ieltsprep.reading.ReadingDtos.PassageSummary;
import com.ieltsprep.reading.ReadingDtos.PassageView;
import com.ieltsprep.reading.ReadingDtos.ResultView;
import com.ieltsprep.reading.ReadingDtos.Scope;
import com.ieltsprep.reading.ReadingDtos.SessionView;
import com.ieltsprep.reading.ReadingDtos.StartRequest;
import com.ieltsprep.reading.ReadingDtos.SubmitRequest;
import com.ieltsprep.session.PracticeSession;
import com.ieltsprep.session.SessionKind;
import com.ieltsprep.session.SessionService;
import com.ieltsprep.session.SessionStatus;
import com.ieltsprep.settings.SettingsService;
import com.ieltsprep.vocab.VocabCapture;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadingService {

    public static final int TEST_SECONDS = 60 * 60;
    public static final int PASSAGE_SECONDS = 20 * 60;

    private final ItemService items;
    private final SessionService sessions;
    private final AttemptRepository attempts;
    private final BandCalculator bands;
    private final SettingsService settings;
    private final VocabCapture vocab;
    private final Clock clock;

    public ReadingService(ItemService items, SessionService sessions, AttemptRepository attempts, BandCalculator bands,
            SettingsService settings, VocabCapture vocab, Clock clock) {
        this.items = items;
        this.sessions = sessions;
        this.attempts = attempts;
        this.bands = bands;
        this.settings = settings;
        this.vocab = vocab;
        this.clock = clock;
    }

    public List<PassageSummary> passages() {
        return items.list(TaskType.READING_PASSAGE).stream()
                .map(i -> new PassageSummary(i.getId(), i.getTitle(), i.getDifficulty(), i.getTopic(), i.getCefr(),
                        i.getQuestionTypes(), i.getTimesServed(), i.getOrigin().name(), i.getSourceUrl()))
                .toList();
    }

    @Transactional
    public SessionView start(StartRequest req, Long mockTestId) {
        List<Long> ids = new ArrayList<>();
        if (req.scope() == Scope.TEST) {
            for (int p = 1; p <= 3; p++) {
                ids.add(items.takeNext(TaskType.READING_PASSAGE, "P" + p).getId());
            }
        } else if (req.itemId() != null) {
            Item item = items.take(req.itemId());
            if (item.getTaskType() != TaskType.READING_PASSAGE) {
                throw ApiException.badRequest("Item " + req.itemId() + " is not a reading passage");
            }
            ids.add(item.getId());
        } else {
            int difficulty = req.difficulty() == null ? 1 : Math.max(1, Math.min(3, req.difficulty()));
            ids.add(items.takeNext(TaskType.READING_PASSAGE, "P" + difficulty).getId());
        }
        SessionKind kind = req.scope() == Scope.TEST ? SessionKind.READING_TEST : SessionKind.READING_PASSAGE;
        int limit = req.scope() == Scope.TEST ? TEST_SECONDS : PASSAGE_SECONDS;
        PracticeSession s = sessions.start(Skill.READING, req.mode(), kind, ids, limit, mockTestId);
        return view(s);
    }

    public SessionView view(long sessionId) {
        return view(sessions.require(sessionId, Skill.READING));
    }

    private SessionView view(PracticeSession s) {
        return new SessionView(s.getId(), s.getMode(), s.getKind(), s.getStartedAt(), s.getTimeLimitSeconds(),
                s.getStatus().name(), passageViews(s));
    }

    private List<PassageView> passageViews(PracticeSession s) {
        List<PassageView> out = new ArrayList<>();
        int offset = 0;
        for (Long id : s.itemIdList()) {
            Item item = items.get(id);
            ReadingPassage p = item.contentAs(ReadingPassage.class);
            int count = Questions.questionCount(p.questionGroups());
            out.add(new PassageView(id, offset, count, p.withoutAnswers(), item.getSourceUrl(), item.getLicence()));
            offset += count;
        }
        return out;
    }

    @Transactional
    public ResultView submit(long sessionId, SubmitRequest req) {
        PracticeSession s = sessions.require(sessionId, Skill.READING);
        sessions.ensureOpen(s);
        Map<Integer, String> answers = req.answers() == null ? Map.of() : req.answers();
        Instant now = Instant.now(clock);
        int offset = 0;
        int raw = 0;
        int max = 0;
        for (Long id : s.itemIdList()) {
            Item item = items.get(id);
            ReadingPassage p = item.contentAs(ReadingPassage.class);
            int count = Questions.questionCount(p.questionGroups());
            Map<Integer, String> local = new HashMap<>();
            for (int n = 1; n <= count; n++) {
                String a = answers.get(offset + n);
                if (a != null) {
                    local.put(n, a);
                }
            }
            List<QuestionResult> results = QuestionMarker.mark(p.questionGroups(), local);
            int score = QuestionMarker.score(results);
            Attempt a = new Attempt();
            a.setSessionId(s.getId());
            a.setItemId(id);
            a.setModule(Skill.READING);
            a.setTaskType(TaskType.READING_PASSAGE.name());
            a.setMyAnswers(Json.write(local));
            a.setRawScore(score);
            a.setMaxScore(count);
            a.setBandEstimate(BigDecimal.valueOf(bands.scaledReadingBand(score, count, settings.get().getExamType())));
            a.setPerQuestion(Json.write(results));
            a.setDurationSeconds(req.secondsPerPassage() == null ? null : req.secondsPerPassage().get(id));
            a.setStatus(AttemptStatus.MARKED);
            a.setSubmittedAt(now);
            a.setGradedAt(now);
            attempts.save(a);
            raw += score;
            max += count;
            offset += count;
        }
        double band = max == 40 ? bands.readingBand(raw, settings.get().getExamType())
                : bands.scaledReadingBand(raw, max, settings.get().getExamType());
        sessions.complete(s, req.timeUsedSeconds(), raw, max, BigDecimal.valueOf(band));
        if (req.flaggedWords() != null) {
            req.flaggedWords().forEach(w -> vocab.capture(w.word(), w.sentence(), "READING_FLAG", "session " + sessionId));
        }
        return result(sessionId);
    }

    public ResultView result(long sessionId) {
        PracticeSession s = sessions.require(sessionId, Skill.READING);
        if (s.getStatus() != SessionStatus.COMPLETED) {
            throw ApiException.conflict("Session " + sessionId + " has not been submitted yet");
        }
        List<Attempt> list = attempts.findBySessionIdOrderByIdAsc(sessionId);
        List<PassageResult> passages = new ArrayList<>();
        int offset = 0;
        int blanks = 0;
        for (Attempt a : list) {
            Item item = items.get(a.getItemId());
            List<QuestionResult> results = Json.read(a.getPerQuestion(), new TypeReference<List<QuestionResult>>() {});
            final int off = offset;
            List<QuestionResult> global = results.stream()
                    .map(r -> new QuestionResult(r.number() + off, r.groupId(), r.questionType(), r.given(), r.expected(),
                            r.correct(), r.blank(), r.justification(), r.location(), r.note()))
                    .toList();
            blanks += (int) results.stream().filter(QuestionResult::blank).count();
            passages.add(new PassageResult(item.getId(), a.getId(), item.getTitle(), offset, a.getRawScore(), a.getMaxScore(),
                    a.getDurationSeconds(), global));
            offset += a.getMaxScore();
        }
        boolean estimate = s.getMaxScore() == null || s.getMaxScore() != 40;
        return new ResultView(s.getId(), s.getMode(), s.getKind(), s.getRawScore(), s.getMaxScore(),
                s.getBandEstimate() == null ? 0 : s.getBandEstimate().doubleValue(), estimate, s.getTimeUsedSeconds(),
                s.getTimeLimitSeconds(), blanks, s.getFinishedAt(), passages, passageViews(s));
    }
}
