package com.ieltsprep.writing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ieltsprep.attempt.Attempt;
import com.ieltsprep.attempt.AttemptRepository;
import com.ieltsprep.attempt.AttemptStatus;
import com.ieltsprep.common.ApiException;
import com.ieltsprep.common.Json;
import com.ieltsprep.common.TextStats;
import com.ieltsprep.content.ExamType;
import com.ieltsprep.content.Item;
import com.ieltsprep.content.ItemService;
import com.ieltsprep.content.Skill;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.content.TopicTaxonomy;
import com.ieltsprep.grading.GradingDispatcher;
import com.ieltsprep.grading.GradingModels.WritingGrade;
import com.ieltsprep.session.PracticeSession;
import com.ieltsprep.session.SessionKind;
import com.ieltsprep.session.SessionService;
import com.ieltsprep.session.SessionStatus;
import com.ieltsprep.settings.SettingsService;
import com.ieltsprep.writing.WritingDtos.AttemptView;
import com.ieltsprep.writing.WritingDtos.PromptSummary;
import com.ieltsprep.writing.WritingDtos.ResultView;
import com.ieltsprep.writing.WritingDtos.Scope;
import com.ieltsprep.writing.WritingDtos.SessionView;
import com.ieltsprep.writing.WritingDtos.StartRequest;
import com.ieltsprep.writing.WritingDtos.SubmitRequest;
import com.ieltsprep.writing.WritingDtos.TaskView;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WritingService {

    private final ItemService items;
    private final SessionService sessions;
    private final AttemptRepository attempts;
    private final SettingsService settings;
    private final TopicTaxonomy topics;
    private final WritingGrader grader;
    private final GradingDispatcher dispatcher;
    private final Clock clock;

    public WritingService(ItemService items, SessionService sessions, AttemptRepository attempts, SettingsService settings,
            TopicTaxonomy topics, WritingGrader grader, GradingDispatcher dispatcher, Clock clock) {
        this.items = items;
        this.sessions = sessions;
        this.attempts = attempts;
        this.settings = settings;
        this.topics = topics;
        this.grader = grader;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    public List<PromptSummary> prompts() {
        List<PromptSummary> out = new ArrayList<>();
        for (TaskType t : List.of(task1Type(), TaskType.WRITING_TASK2)) {
            items.list(t).forEach(i -> out.add(new PromptSummary(i.getId(), t.name(), i.getVariant(), i.getTitle(), i.getTopic(),
                    i.getTimesServed(), i.getOrigin().name())));
        }
        return out;
    }

    public Map<String, Integer> topicCoverage() {
        return topics.coverage();
    }

    @Transactional
    public SessionView start(StartRequest req, Long mockTestId) {
        List<Long> ids = new ArrayList<>();
        if (req.itemId() != null) {
            Item item = items.take(req.itemId());
            if (item.getModule() != Skill.WRITING) {
                throw ApiException.badRequest("Item " + req.itemId() + " is not a writing prompt");
            }
            ids.add(item.getId());
            if (item.getTaskType() == TaskType.WRITING_TASK2) {
                topics.recordUse(item.getTopic());
            }
        } else {
            if (req.scope() != Scope.TASK2) {
                ids.add(items.takeNext(task1Type(), null).getId());
            }
            if (req.scope() != Scope.TASK1) {
                ids.add(takeTask2().getId());
            }
        }
        int limit = ids.stream().mapToInt(id -> minutes(items.get(id).getTaskType()) * 60).sum();
        SessionKind kind = ids.size() == 2 ? SessionKind.WRITING_TEST
                : items.get(ids.getFirst()).getTaskType() == TaskType.WRITING_TASK2 ? SessionKind.WRITING_TASK2 : SessionKind.WRITING_TASK1;
        PracticeSession s = sessions.start(Skill.WRITING, req.mode(), kind, ids, limit, mockTestId);
        return view(s);
    }

    /** Task 2 prompts rotate through the topic taxonomy: least-practised topic first. */
    private Item takeTask2() {
        Map<String, Integer> coverage = topics.coverage();
        Item next = items.list(TaskType.WRITING_TASK2).stream()
                .min(Comparator.comparingInt((Item i) -> coverage.getOrDefault(i.getTopic(), 0))
                        .thenComparingInt(Item::getTimesServed).thenComparing(Item::getId))
                .orElseThrow(() -> ApiException.notFound("Writing Task 2 prompt"));
        topics.recordUse(next.getTopic());
        return items.take(next.getId());
    }

    public SessionView view(long sessionId) {
        return view(sessions.require(sessionId, Skill.WRITING));
    }

    private SessionView view(PracticeSession s) {
        return new SessionView(s.getId(), s.getMode(), s.getKind(), s.getStartedAt(), s.getTimeLimitSeconds(), s.getStatus().name(),
                tasks(s), s.getMockTestId());
    }

    private List<TaskView> tasks(PracticeSession s) {
        return s.itemIdList().stream().map(id -> taskView(items.get(id))).toList();
    }

    static TaskView taskView(Item item) {
        TaskType t = item.getTaskType();
        return new TaskView(item.getId(), t.name(), t == TaskType.WRITING_TASK2 ? 2 : 1, minutes(t), minWords(t),
                t == TaskType.WRITING_TASK1_ACADEMIC ? item.contentAs(WritingPrompts.Task1Academic.class) : null,
                t == TaskType.WRITING_TASK1_GENERAL ? item.contentAs(WritingPrompts.Task1General.class) : null,
                t == TaskType.WRITING_TASK2 ? item.contentAs(WritingPrompts.Task2.class) : null);
    }

    /** Locks the responses, records them and queues grading. */
    @Transactional
    public ResultView submit(long sessionId, SubmitRequest req) {
        PracticeSession s = sessions.require(sessionId, Skill.WRITING);
        sessions.ensureOpen(s);
        Instant now = Instant.now(clock);
        List<Long> toGrade = new ArrayList<>();
        for (Long itemId : s.itemIdList()) {
            Item item = items.get(itemId);
            WritingDtos.ResponseRequest r = req.responses() == null ? null
                    : req.responses().stream().filter(x -> x.itemId() == itemId).findFirst().orElse(null);
            String text = r == null || r.text() == null ? "" : r.text().strip();
            Attempt a = new Attempt();
            a.setSessionId(s.getId());
            a.setItemId(itemId);
            a.setModule(Skill.WRITING);
            a.setTaskType(item.getTaskType().name());
            a.setMyAnswers(Json.write(Map.of("text", text)));
            a.setWordCount(TextStats.words(text));
            a.setDurationSeconds(r == null ? null : r.secondsSpent());
            a.setSubmittedAt(now);
            a.setStatus(text.isBlank() ? AttemptStatus.GRADING_FAILED : AttemptStatus.GRADING);
            if (text.isBlank()) {
                a.setFeedback(Json.write(Map.of("error", "No response was written for this task.")));
            }
            attempts.save(a);
            if (!text.isBlank()) {
                toGrade.add(a.getId());
            }
        }
        sessions.complete(s, req.timeUsedSeconds(), null, null, null);
        toGrade.forEach(id -> dispatcher.dispatch("grade writing " + id, () -> grader.grade(id)));
        return result(sessionId);
    }

    @Transactional
    public ResultView regrade(long attemptId) {
        Attempt a = attempts.findById(attemptId).orElseThrow(() -> ApiException.notFound("Attempt " + attemptId));
        if (a.getModule() != Skill.WRITING) {
            throw ApiException.badRequest("Not a writing attempt");
        }
        if (a.getStatus() == AttemptStatus.GRADING) {
            throw ApiException.conflict("Already grading");
        }
        a.setStatus(AttemptStatus.GRADING);
        attempts.save(a);
        dispatcher.dispatch("regrade writing " + attemptId, () -> grader.grade(attemptId));
        return result(a.getSessionId());
    }

    public ResultView result(long sessionId) {
        PracticeSession s = sessions.require(sessionId, Skill.WRITING);
        if (s.getStatus() != SessionStatus.COMPLETED) {
            throw ApiException.conflict("Session " + sessionId + " has not been submitted yet");
        }
        List<AttemptView> views = attempts.findBySessionIdOrderByIdAsc(sessionId).stream().map(WritingService::attemptView).toList();
        boolean grading = views.stream().anyMatch(v -> v.status().equals(AttemptStatus.GRADING.name()));
        boolean failed = views.stream().anyMatch(v -> v.status().equals(AttemptStatus.GRADING_FAILED.name()));
        String status = grading ? "GRADING" : failed ? (views.stream().anyMatch(v -> v.band() != null) ? "PARTIAL" : "FAILED") : "GRADED";
        Double band = s.getBandEstimate() == null ? null : s.getBandEstimate().doubleValue();
        return new ResultView(s.getId(), s.getMode(), s.getKind(), status, band, s.getKind() != SessionKind.WRITING_TEST,
                s.getTimeUsedSeconds(), s.getTimeLimitSeconds(), views, tasks(s));
    }

    static AttemptView attemptView(Attempt a) {
        Map<String, Object> answers = Json.read(a.getMyAnswers(), new TypeReference<Map<String, Object>>() {});
        String text = answers == null ? "" : String.valueOf(answers.getOrDefault("text", ""));
        WritingGrade grade = null;
        String error = null;
        if (a.getStatus() == AttemptStatus.GRADED) {
            grade = Json.read(a.getFeedback(), WritingGrade.class);
        } else if (a.getFeedback() != null) {
            Map<String, Object> f = Json.read(a.getFeedback(), new TypeReference<Map<String, Object>>() {});
            error = f == null ? null : String.valueOf(f.getOrDefault("error", ""));
        }
        Map<String, Integer> criteria = a.getPerCriterionBands() == null ? null
                : Json.read(a.getPerCriterionBands(), new TypeReference<Map<String, Integer>>() {});
        TaskType t = TaskType.valueOf(a.getTaskType());
        return new AttemptView(a.getId(), a.getItemId(), a.getTaskType(), t == TaskType.WRITING_TASK2 ? 2 : 1, text,
                a.getWordCount() == null ? 0 : a.getWordCount(), a.getStatus().name(),
                a.getBandEstimate() == null ? null : a.getBandEstimate().doubleValue(), criteria, grade, error, a.getDurationSeconds(),
                a.getSubmittedAt(), a.getGradedAt());
    }

    private TaskType task1Type() {
        return settings.get().getExamType() == ExamType.GENERAL ? TaskType.WRITING_TASK1_GENERAL : TaskType.WRITING_TASK1_ACADEMIC;
    }

    static int minutes(TaskType t) {
        return t == TaskType.WRITING_TASK2 ? 40 : 20;
    }

    static int minWords(TaskType t) {
        return t == TaskType.WRITING_TASK2 ? 250 : 150;
    }
}
