package com.ieltsprep.plan;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Everything the planner needs to know about the candidate, gathered from settings, the dashboard, grammar progress,
 * the vocabulary deck and the latest coach report. Also sent to Claude (as JSON) when the plan is personalised.
 *
 * @param bands            current band per skill (LISTENING/READING/WRITING/SPEAKING); null when not yet measured
 * @param writingCriteria  recent average per Writing criterion (e.g. LEXICAL_RESOURCE → 6.3)
 * @param daysSinceMock    null when no full mock test has been taken
 */
public record PlanInputs(
        LocalDate today,
        LocalDate testDate,
        double targetBand,
        double startingBand,
        Map<String, Double> bands,
        int dailyMinutes,
        List<WeakSpot> weakQuestionTypes,
        List<ErrorFocus> topErrors,
        List<AreaFocus> weakGrammarAreas,
        boolean diagnosticDone,
        long vocabDue,
        long vocabTotal,
        Map<String, Integer> sessionsLast7Days,
        Integer daysSinceMock,
        Map<String, Double> writingCriteria,
        Map<String, Double> speakingCriteria,
        List<String> coachPriorities) {

    public record WeakSpot(String module, String questionType, double accuracy, int total) {}

    public record ErrorFocus(String subtype, String area, int total, String trend) {}

    public record AreaFocus(String key, String name, Double proficiency) {}
}
