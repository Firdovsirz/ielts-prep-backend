package com.ieltsprep.speaking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ieltsprep.attempt.Attempt;
import com.ieltsprep.attempt.AttemptRepository;
import com.ieltsprep.attempt.AttemptStatus;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.ApiException;
import com.ieltsprep.common.Json;
import com.ieltsprep.common.TextStats;
import com.ieltsprep.config.AppProperties;
import com.ieltsprep.content.Item;
import com.ieltsprep.content.ItemService;
import com.ieltsprep.content.Skill;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.grading.GradingDispatcher;
import com.ieltsprep.grading.GradingModels.SpeakingGrade;
import com.ieltsprep.session.PracticeSession;
import com.ieltsprep.session.SessionKind;
import com.ieltsprep.session.SessionService;
import com.ieltsprep.session.SessionStatus;
import com.ieltsprep.speaking.SpeakingDtos.ResponseView;
import com.ieltsprep.speaking.SpeakingDtos.ResultView;
import com.ieltsprep.speaking.SpeakingDtos.Scope;
import com.ieltsprep.speaking.SpeakingDtos.SessionView;
import com.ieltsprep.speaking.SpeakingDtos.StartRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SpeakingService {

    private final ItemService items;
    private final SessionService sessions;
    private final SpeakingResponseRepository responses;
    private final AttemptRepository attempts;
    private final ExaminerService examiner;
    private final TranscriptionService transcription;
    private final SpeakingGrader grader;
    private final GradingDispatcher dispatcher;
    private final ClaudeService claude;
    private final AppProperties props;
    private final Clock clock;

    public SpeakingService(ItemService items, SessionService sessions, SpeakingResponseRepository responses, AttemptRepository attempts,
            ExaminerService examiner, TranscriptionService transcription, SpeakingGrader grader, GradingDispatcher dispatcher,
            ClaudeService claude, AppProperties props, Clock clock) {
        this.items = items;
        this.sessions = sessions;
        this.responses = responses;
        this.attempts = attempts;
        this.examiner = examiner;
        this.transcription = transcription;
        this.grader = grader;
        this.dispatcher = dispatcher;
        this.claude = claude;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public SessionView start(StartRequest req, Long mockTestId) {
        List<Long> ids = new ArrayList<>();
        if (req.scope() == Scope.TEST || req.scope() == Scope.PART1) {
            ids.add(items.takeNext(TaskType.SPEAKING_PART1, null).getId());
        }
        Item part2 = null;
        if (req.scope() == Scope.TEST || req.scope() == Scope.PART2) {
            part2 = items.takeNext(TaskType.SPEAKING_PART2, null);
            ids.add(part2.getId());
        }
        if (req.scope() == Scope.TEST || req.scope() == Scope.PART3) {
            ids.add(linkedPart3(part2).getId());
        }
        SessionKind kind = req.scope() == Scope.TEST ? SessionKind.SPEAKING_TEST : SessionKind.SPEAKING_PART;
        PracticeSession s = sessions.start(Skill.SPEAKING, req.mode(), kind, ids, null, mockTestId);
        s.setOptions(Json.write(Map.of("conversational", Boolean.TRUE.equals(req.conversational()), "scope", req.scope().name())));
        sessions.save(s);
        return view(s);
    }

    /** Part 3 is the discussion linked to the Part 2 card (same topic/theme) when one exists. */
    private Item linkedPart3(Item part2) {
        if (part2 != null) {
            SpeakingPrompts.Part2 card = part2.contentAs(SpeakingPrompts.Part2.class);
            Optional<Item> linked = items.list(TaskType.SPEAKING_PART3).stream().filter(i -> {
                SpeakingPrompts.Part3 p3 = i.contentAs(SpeakingPrompts.Part3.class);
                return p3.linkedPart2Topic().equalsIgnoreCase(card.topic()) || p3.theme().equalsIgnoreCase(card.theme());
            }).findFirst();
            if (linked.isPresent()) {
                return items.take(linked.get().getId());
            }
        }
        return items.takeNext(TaskType.SPEAKING_PART3, null);
    }

    public SessionView view(long sessionId) {
        return view(sessions.require(sessionId, Skill.SPEAKING));
    }

    private SessionView view(PracticeSession s) {
        return new SessionView(s.getId(), s.getMode(), s.getKind(), s.getStatus().name(), s.getStartedAt(), plan(s), conversational(s),
                claude.isAvailable(), transcription.mode(), responses.findBySessionIdOrderByIdAsc(s.getId()).stream().map(SpeakingService::view).toList(), s.getMockTestId());
    }

    SpeakingPlan plan(PracticeSession s) {
        SpeakingPrompts.Part1 p1 = null;
        SpeakingPrompts.Part2 p2 = null;
        SpeakingPrompts.Part3 p3 = null;
        for (Long id : s.itemIdList()) {
            Item i = items.get(id);
            switch (i.getTaskType()) {
                case SPEAKING_PART1 -> p1 = i.contentAs(SpeakingPrompts.Part1.class);
                case SPEAKING_PART2 -> p2 = i.contentAs(SpeakingPrompts.Part2.class);
                case SPEAKING_PART3 -> p3 = i.contentAs(SpeakingPrompts.Part3.class);
                default -> { }
            }
        }
        return new SpeakingPlan(p1, p2, p3);
    }

    private static boolean conversational(PracticeSession s) {
        Map<String, Object> o = s.getOptions() == null ? Map.of() : Json.read(s.getOptions(), new TypeReference<Map<String, Object>>() {});
        return Boolean.TRUE.equals(o.get("conversational"));
    }

    public ExaminerTurn next(long sessionId) {
        PracticeSession s = sessions.require(sessionId, Skill.SPEAKING);
        sessions.ensureOpen(s);
        return examiner.next(plan(s), responses.findBySessionIdOrderByIdAsc(sessionId), conversational(s));
    }

    /** Stores one answer: audio file (optional) plus transcript (from the browser, or Whisper when configured). */
    @Transactional
    public ResponseView record(long sessionId, int part, int questionIndex, String question, String transcript, Integer durationSeconds,
            MultipartFile audio) {
        PracticeSession s = sessions.require(sessionId, Skill.SPEAKING);
        sessions.ensureOpen(s);
        SpeakingResponse r = new SpeakingResponse();
        r.setSessionId(sessionId);
        r.setPart(part);
        r.setQuestionIndex(questionIndex);
        r.setQuestion(cut(question, 2000));
        r.setTranscript(cut(transcript == null ? "" : transcript.strip(), 20000));
        r.setDurationSeconds(durationSeconds);
        r.setCreatedAt(Instant.now(clock));
        responses.save(r);
        if (audio != null && !audio.isEmpty()) {
            Path file = saveAudio(sessionId, r.getId(), audio);
            r.setAudioPath(props.recordingsPath().relativize(file).toString());
            transcription.transcribe(file, audio.getContentType()).filter(t -> !t.isBlank()).ifPresent(t -> r.setTranscript(cut(t, 20000)));
        }
        return view(responses.save(r));
    }

    private Path saveAudio(long sessionId, long responseId, MultipartFile audio) {
        String type = audio.getContentType() == null ? "" : audio.getContentType().toLowerCase(Locale.ROOT);
        String ext = type.contains("mp4") || type.contains("m4a") ? "m4a" : type.contains("ogg") ? "ogg" : type.contains("wav") ? "wav" : "webm";
        try {
            Path dir = props.recordingsPath().resolve(String.valueOf(sessionId));
            Files.createDirectories(dir);
            Path file = dir.resolve(responseId + "." + ext);
            audio.transferTo(file);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store recording", e);
        }
    }

    public Path audioFile(long responseId) {
        SpeakingResponse r = responses.findById(responseId).orElseThrow(() -> ApiException.notFound("Response " + responseId));
        if (r.getAudioPath() == null) {
            throw ApiException.notFound("Recording for response " + responseId);
        }
        Path file = props.recordingsPath().resolve(r.getAudioPath()).normalize();
        if (!file.startsWith(props.recordingsPath()) || !Files.exists(file)) {
            throw ApiException.notFound("Recording file");
        }
        return file;
    }

    /** Ends the test: one attempt holding every answer, graded asynchronously. */
    @Transactional
    public ResultView finish(long sessionId) {
        PracticeSession s = sessions.require(sessionId, Skill.SPEAKING);
        sessions.ensureOpen(s);
        List<SpeakingResponse> list = responses.findBySessionIdOrderByIdAsc(sessionId);
        if (list.isEmpty()) {
            throw ApiException.badRequest("Record at least one answer before finishing");
        }
        Attempt a = new Attempt();
        a.setSessionId(sessionId);
        a.setItemId(s.itemIdList().size() >= 2 ? s.itemIdList().get(1) : s.itemIdList().getFirst());
        a.setModule(Skill.SPEAKING);
        a.setTaskType(taskType(s));
        a.setMyAnswers(Json.write(Map.of("responses", list.stream().map(r -> Map.of("part", r.getPart(), "question", r.getQuestion(),
                "transcript", r.getTranscript() == null ? "" : r.getTranscript(), "duration_seconds",
                r.getDurationSeconds() == null ? 0 : r.getDurationSeconds(), "response_id", r.getId())).toList())));
        a.setWordCount(list.stream().mapToInt(r -> TextStats.words(r.getTranscript())).sum());
        a.setDurationSeconds(list.stream().mapToInt(r -> r.getDurationSeconds() == null ? 0 : r.getDurationSeconds()).sum());
        a.setStatus(AttemptStatus.GRADING);
        a.setSubmittedAt(Instant.now(clock));
        attempts.save(a);
        list.forEach(r -> r.setAttemptId(a.getId()));
        sessions.complete(s, a.getDurationSeconds(), null, null, null);
        dispatcher.dispatch("grade speaking " + a.getId(), () -> grader.grade(a.getId()));
        return result(sessionId);
    }

    @Transactional
    public ResultView regrade(long attemptId) {
        Attempt a = attempts.findById(attemptId).orElseThrow(() -> ApiException.notFound("Attempt " + attemptId));
        if (a.getStatus() == AttemptStatus.GRADING) {
            throw ApiException.conflict("Already grading");
        }
        a.setStatus(AttemptStatus.GRADING);
        attempts.save(a);
        dispatcher.dispatch("regrade speaking " + attemptId, () -> grader.grade(attemptId));
        return result(a.getSessionId());
    }

    public ResultView result(long sessionId) {
        PracticeSession s = sessions.require(sessionId, Skill.SPEAKING);
        if (s.getStatus() != SessionStatus.COMPLETED) {
            throw ApiException.conflict("Not finished yet");
        }
        Attempt a = attempts.findBySessionIdOrderByIdAsc(sessionId).stream().findFirst().orElse(null);
        List<ResponseView> list = responses.findBySessionIdOrderByIdAsc(sessionId).stream().map(SpeakingService::view).toList();
        SpeakingGrade grade = null;
        String error = null;
        Map<String, Integer> criteria = null;
        if (a != null && a.getStatus() == AttemptStatus.GRADED) {
            grade = Json.read(a.getFeedback(), SpeakingGrade.class);
            criteria = Json.read(a.getPerCriterionBands(), new TypeReference<Map<String, Integer>>() {});
        } else if (a != null && a.getFeedback() != null) {
            error = String.valueOf(Json.read(a.getFeedback(), new TypeReference<Map<String, Object>>() {}).getOrDefault("error", ""));
        }
        int words = a == null || a.getWordCount() == null ? 0 : a.getWordCount();
        Double wpm = a == null || a.getDurationSeconds() == null || a.getDurationSeconds() == 0 ? null
                : Math.round(words * 600.0 / a.getDurationSeconds()) / 10.0;
        return new ResultView(sessionId, s.getMode(), s.getKind(), a == null ? "NONE" : a.getStatus().name(), a == null ? null : a.getId(),
                a == null || a.getBandEstimate() == null ? null : a.getBandEstimate().doubleValue(), criteria, grade, error, words, wpm,
                list, plan(s));
    }

    private String taskType(PracticeSession s) {
        if (s.getKind() == SessionKind.SPEAKING_TEST) {
            return "SPEAKING_TEST";
        }
        return items.get(s.itemIdList().getFirst()).getTaskType().name();
    }

    static ResponseView view(SpeakingResponse r) {
        return new ResponseView(r.getId(), r.getPart(), r.getQuestionIndex(), r.getQuestion(), r.getTranscript(), r.getDurationSeconds(),
                r.getAudioPath() != null);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
