package com.ieltsprep.speaking;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ieltsprep.claude.ClaudeCall;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decides the examiner's next turn from the plan and the answers recorded so far. The Part 2 long turn always follows
 * the real script (cue card, one minute to prepare, up to two minutes to talk). Part 1 and Part 3 are scripted, or —
 * in conversation mode with an API key — Claude chooses follow-ups within hard per-part limits.
 */
@Service
public class ExaminerService {

    private static final Logger log = LoggerFactory.getLogger(ExaminerService.class);
    static final int PART1_MAX = 12;
    static final int PART3_MAX = 8;
    static final int PREP_SECONDS = 60;
    static final int TALK_SECONDS = 120;

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ClaudeTurn(String utterance, String stage, String reason) {}

    private final ClaudeService claude;

    public ExaminerService(ClaudeService claude) {
        this.claude = claude;
    }

    public ExaminerTurn next(SpeakingPlan plan, List<SpeakingResponse> answers, boolean conversational) {
        long p1 = answers.stream().filter(a -> a.getPart() == 1).count();
        boolean talked = answers.stream().anyMatch(a -> a.getPart() == 2 && a.getQuestionIndex() == 0);
        long rounding = answers.stream().filter(a -> a.getPart() == 2 && a.getQuestionIndex() > 0).count();
        long p3 = answers.stream().filter(a -> a.getPart() == 3).count();

        if (plan.part1() != null) {
            int planned = plan.part1().topics().stream().mapToInt(t -> t.questions().size()).sum();
            boolean useClaude = conversational && claude.isAvailable() && p1 > 0 && p1 < PART1_MAX;
            if (useClaude) {
                ExaminerTurn t = ask(plan, answers, 1, "Part 1: " + p1 + " answers so far (maximum " + PART1_MAX
                        + "). Cover all three topics; say when Part 1 is complete by moving to stage PART3.", (int) p1);
                if (t != null && t.stage().equals("PART1")) {
                    return t;
                }
                if (t == null && p1 < planned) {
                    return scriptedPart1(plan, (int) p1);
                }
                // otherwise Claude has closed Part 1
            } else if (p1 < planned && p1 < PART1_MAX) {
                return scriptedPart1(plan, (int) p1);
            }
        }
        return nextAfterPart1(plan, answers, talked, rounding, p3, conversational);
    }

    private ExaminerTurn nextAfterPart1(SpeakingPlan plan, List<SpeakingResponse> answers, boolean talked, long rounding, long p3,
            boolean conversational) {
        if (plan.part2() != null && !talked) {
            SpeakingPrompts.CueCard card = plan.part2().cueCard();
            String intro = (plan.part1() != null ? "Thank you. " : "")
                    + "Now, I'm going to give you a topic and I'd like you to talk about it for one to two minutes. Before you talk, "
                    + "you'll have one minute to think about what you're going to say. You can make some notes if you wish. "
                    + "Here is your topic. " + card.prompt();
            return new ExaminerTurn("PART2_LONG_TURN", 2, 0, intro, false, PREP_SECONDS, TALK_SECONDS, null);
        }
        if (plan.part2() != null && rounding < Math.min(1, plan.part2().roundingOffQuestions().size())) {
            return new ExaminerTurn("PART2_ROUNDING", 2, (int) rounding + 1,
                    "Thank you. " + plan.part2().roundingOffQuestions().get((int) rounding), false, null, null, 30);
        }
        List<SpeakingPrompts.Part3Question> p3q = plan.part3() == null ? List.of() : plan.part3().questions();
        int p3Limit = Math.min(PART3_MAX, conversational ? PART3_MAX : p3q.size());
        if (plan.part3() != null && p3 < p3Limit && (conversational || p3 < p3q.size())) {
            if (conversational && claude.isAvailable()) {
                ExaminerTurn t = ask(plan, answers, 3, "Part 3 is in progress: " + p3 + " answers so far (maximum " + PART3_MAX + ").", (int) p3);
                if (t != null && t.stage().equals("PART3")) {
                    return t;
                }
            }
            if (p3 < p3q.size()) {
                String lead = p3 == 0 ? "We've been talking about " + plan.part3().theme().toLowerCase()
                        + ", and I'd like to discuss with you one or two more general questions related to this. " : "";
                return new ExaminerTurn("PART3", 3, (int) p3, lead + p3q.get((int) p3).question(), false, null, null, 90);
            }
        }
        return new ExaminerTurn("END", 0, 0, "Thank you. That is the end of the speaking test.", false, null, null, null);
    }

    private ExaminerTurn ask(SpeakingPlan plan, List<SpeakingResponse> answers, int part, String status, int index) {
        try {
            String history = answers.stream()
                    .map(a -> "Examiner (Part " + a.getPart() + "): " + a.getQuestion() + "\nCandidate: "
                            + (a.getTranscript() == null || a.getTranscript().isBlank() ? "(no transcript)" : a.getTranscript()))
                    .collect(Collectors.joining("\n"));
            ClaudeTurn t = claude.call(ClaudeCall.of("speaking-examiner", Map.of("plan", Json.pretty(plan), "current_part",
                    "Part " + part, "part_status", status, "history", history.isBlank() ? "(none yet)" : history), ClaudeTurn.class)
                    .ref("examiner part " + part));
            return new ExaminerTurn(t.stage(), "END".equals(t.stage()) ? 0 : part, index, t.utterance(), true, null, null,
                    part == 1 ? 45 : 90);
        } catch (Exception e) {
            log.warn("Conversational examiner unavailable, using the script: {}", e.getMessage());
            return null;
        }
    }

    private static ExaminerTurn scriptedPart1(SpeakingPlan plan, int index) {
        return new ExaminerTurn("PART1", 1, index, part1Utterance(plan.part1(), index), false, null, null, 45);
    }

    static String part1Utterance(SpeakingPrompts.Part1 p1, int index) {
        int seen = 0;
        for (int t = 0; t < p1.topics().size(); t++) {
            SpeakingPrompts.Part1Topic topic = p1.topics().get(t);
            for (int q = 0; q < topic.questions().size(); q++, seen++) {
                if (seen == index) {
                    String question = topic.questions().get(q);
                    if (index == 0) {
                        return "Good morning. In this first part, I'd like to ask you some questions about yourself. Let's talk about "
                                + topic.topic().toLowerCase() + ". " + question;
                    }
                    return q == 0 ? "Now let's talk about " + topic.topic().toLowerCase() + ". " + question : question;
                }
            }
        }
        return "";
    }
}
