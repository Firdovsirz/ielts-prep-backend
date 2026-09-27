package com.ieltsprep.listening;

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
import com.ieltsprep.listening.ListeningDtos.ResultView;
import com.ieltsprep.listening.ListeningDtos.Scope;
import com.ieltsprep.listening.ListeningDtos.SectionResult;
import com.ieltsprep.listening.ListeningDtos.SectionSummary;
import com.ieltsprep.listening.ListeningDtos.SectionView;
import com.ieltsprep.listening.ListeningDtos.SessionView;
import com.ieltsprep.listening.ListeningDtos.StartRequest;
import com.ieltsprep.listening.ListeningDtos.SubmitRequest;
import com.ieltsprep.marking.QuestionMarker;
import com.ieltsprep.marking.QuestionMarker.QuestionResult;
import com.ieltsprep.session.PracticeSession;
import com.ieltsprep.session.SessionKind;
import com.ieltsprep.session.SessionService;
import com.ieltsprep.session.SessionStatus;
import com.ieltsprep.settings.SettingsService;
import com.ieltsprep.settings.UserSettings;
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
public class ListeningService {

    private final ItemService items;
    private final SessionService sessions;
    private final AttemptRepository attempts;
    private final BandCalculator bands;
    private final SettingsService settings;
    private final Clock clock;

    public ListeningService(ItemService items, SessionService sessions, AttemptRepository attempts, BandCalculator bands,
            SettingsService settings, Clock clock) {
        this.items = items;
        this.sessions = sessions;
        this.attempts = attempts;
        this.bands = bands;
        this.settings = settings;
        this.clock = clock;
    }

    public List<SectionSummary> sections() {
        return items.list(TaskType.LISTENING_SECTION).stream()
                .map(i -> new SectionSummary(i.getId(), i.getTitle(), i.getDifficulty(), i.getTopic(), i.getQuestionTypes(),
                        i.getTimesServed(), i.getOrigin().name()))
                .toList();
    }

    @Transactional
    public SessionView start(StartRequest req, Long mockTestId) {
        List<Long> ids = new ArrayList<>();
        if (req.scope() == Scope.TEST) {
            for (int s = 1; s <= 4; s++) {
                ids.add(items.takeNext(TaskType.LISTENING_SECTION, "S" + s).getId());
            }
        } else if (req.itemId() != null) {
            Item item = items.take(req.itemId());
            if (item.getTaskType() != TaskType.LISTENING_SECTION) {
                throw ApiException.badRequest("Item " + req.itemId() + " is not a listening section");
            }
            ids.add(item.getId());
        } else {
            int section = req.section() == null ? 1 : Math.max(1, Math.min(4, req.section()));
            ids.add(items.takeNext(TaskType.LISTENING_SECTION, "S" + section).getId());
        }
        SessionKind kind = req.scope() == Scope.TEST ? SessionKind.LISTENING_TEST : SessionKind.LISTENING_SECTION;
        // no wall-clock limit: the audio paces the test; the transfer time is enforced by the player
        PracticeSession s = sessions.start(Skill.LISTENING, req.mode(), kind, ids, null, mockTestId);
        return view(s);
    }

    public SessionView view(long sessionId) {
        return view(sessions.require(sessionId, Skill.LISTENING));
    }

    private SessionView view(PracticeSession s) {
        UserSettings u = settings.get();
        return new SessionView(s.getId(), s.getMode(), s.getKind(), s.getStartedAt(), s.getStatus().name(),
                u.getListeningReadingSeconds(), u.getListeningTransferMinutes(), u.getSpeechRate(), sectionViews(s), s.getMockTestId());
    }

    private List<SectionView> sectionViews(PracticeSession s) {
        List<SectionView> out = new ArrayList<>();
        int offset = 0;
        for (Long id : s.itemIdList()) {
            ListeningSection sec = items.get(id).contentAs(ListeningSection.class);
            int count = Questions.questionCount(sec.questionGroups());
            out.add(new SectionView(id, offset, count, sec.withoutAnswers(true)));
            offset += count;
        }
        return out;
    }

    @Transactional
    public ResultView submit(long sessionId, SubmitRequest req) {
        PracticeSession s = sessions.require(sessionId, Skill.LISTENING);
        sessions.ensureOpen(s);
        Map<Integer, String> answers = req.answers() == null ? Map.of() : req.answers();
        Instant now = Instant.now(clock);
        int offset = 0;
        int raw = 0;
        int max = 0;
        for (Long id : s.itemIdList()) {
            ListeningSection sec = items.get(id).contentAs(ListeningSection.class);
            int count = Questions.questionCount(sec.questionGroups());
            Map<Integer, String> local = new HashMap<>();
            for (int n = 1; n <= count; n++) {
                String a = answers.get(offset + n);
                if (a != null) {
                    local.put(n, a);
                }
            }
            List<QuestionResult> results = QuestionMarker.mark(sec.questionGroups(), local);
            int score = QuestionMarker.score(results);
            Attempt a = new Attempt();
            a.setSessionId(s.getId());
            a.setItemId(id);
            a.setModule(Skill.LISTENING);
            a.setTaskType(TaskType.LISTENING_SECTION.name());
            a.setMyAnswers(Json.write(Map.of("answers", local, "replays",
                    req.replaysPerSection() == null ? 0 : req.replaysPerSection().getOrDefault(id, 0))));
            a.setRawScore(score);
            a.setMaxScore(count);
            a.setBandEstimate(BigDecimal.valueOf(bands.scaledListeningBand(score, count)));
            a.setPerQuestion(Json.write(results));
            a.setStatus(AttemptStatus.MARKED);
            a.setSubmittedAt(now);
            a.setGradedAt(now);
            attempts.save(a);
            raw += score;
            max += count;
            offset += count;
        }
        double band = max == 40 ? bands.listeningBand(raw) : bands.scaledListeningBand(raw, max);
        sessions.complete(s, req.timeUsedSeconds(), raw, max, BigDecimal.valueOf(band));
        return result(sessionId);
    }

    public ResultView result(long sessionId) {
        PracticeSession s = sessions.require(sessionId, Skill.LISTENING);
        if (s.getStatus() != SessionStatus.COMPLETED) {
            throw ApiException.conflict("Session " + sessionId + " has not been submitted yet");
        }
        List<SectionResult> out = new ArrayList<>();
        int offset = 0;
        int blanks = 0;
        for (Attempt a : attempts.findBySessionIdOrderByIdAsc(sessionId)) {
            Item item = items.get(a.getItemId());
            ListeningSection sec = item.contentAs(ListeningSection.class);
            List<QuestionResult> results = Json.read(a.getPerQuestion(), new TypeReference<List<QuestionResult>>() {});
            final int off = offset;
            blanks += (int) results.stream().filter(QuestionResult::blank).count();
            out.add(new SectionResult(item.getId(), a.getId(), item.getTitle(), sec.section(), offset, a.getRawScore(), a.getMaxScore(),
                    results.stream().map(r -> new QuestionResult(r.number() + off, r.groupId(), r.questionType(), r.given(), r.expected(),
                            r.correct(), r.blank(), r.justification(), r.location(), r.note())).toList()));
            offset += a.getMaxScore();
        }
        return new ResultView(s.getId(), s.getMode(), s.getKind(), s.getRawScore(), s.getMaxScore(),
                s.getBandEstimate() == null ? 0 : s.getBandEstimate().doubleValue(), s.getMaxScore() == null || s.getMaxScore() != 40,
                s.getTimeUsedSeconds(), blanks, s.getFinishedAt(), out, sectionViews(s));
    }
}
