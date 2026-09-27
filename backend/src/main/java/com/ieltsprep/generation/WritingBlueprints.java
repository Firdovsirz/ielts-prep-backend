package com.ieltsprep.generation;

import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.content.TopicTaxonomy;
import com.ieltsprep.content.VerificationStatus;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;

/** Blueprints for Writing Task 1 (Academic and General) and Task 2 prompts. */
@Configuration
public class WritingBlueprints {

    static final List<String> CHART_TYPES = List.of("LINE", "BAR", "PIE", "TABLE", "PROCESS", "MAP");
    static final List<String> LETTER_TYPES = List.of("FORMAL", "SEMI_FORMAL", "INFORMAL");
    static final List<String> ESSAY_TYPES = List.of("OPINION", "DISCUSSION", "ADVANTAGES_DISADVANTAGES", "PROBLEM_SOLUTION", "DOUBLE_QUESTION");

    static String leastUsed(ItemRepository items, TaskType type, List<String> variants) {
        return variants.stream()
                .min(Comparator.comparingInt((String v) -> items.findByTaskTypeAndVariantAndVerificationStatusOrderByIdAsc(type, v,
                        VerificationStatus.VERIFIED).size()).thenComparing(v -> ThreadLocalRandom.current().nextInt()))
                .orElseThrow();
    }

    @Bean
    Blueprint writingTask1AcademicBlueprint(ItemRepository items) {
        return new Blueprint() {
            @Override
            public TaskType type() {
                return TaskType.WRITING_TASK1_ACADEMIC;
            }

            @Override
            public List<String> bufferVariants() {
                return java.util.Collections.singletonList(null);
            }

            @Override
            public GenerationPlan plan(GenerationRequest request) {
                String chart = request.variant() != null ? request.variant() : leastUsed(items, type(), CHART_TYPES);
                Map<String, Object> vars = new HashMap<>();
                vars.put("chart_type", chart);
                vars.put("avoid_titles", String.join("; ", items.recentTitles(type(), PageRequest.of(0, 30))));
                return new GenerationPlan("writing-task1-academic-generate", vars, null, "Original content", chart);
            }

            @Override
            public String verifyPrompt() {
                return "content-review";
            }

            @Override
            public Map<String, Object> verifyVars(Object content) {
                return ContentReviewSupport.vars("IELTS Academic Writing Task 1 prompt with figure data", content, """
                        - The prompt wording matches the real test for this figure type and ends with the standard "Summarise the information…" sentence.
                        - The data are internally consistent and plausible (pie charts sum to ~100, series lengths match categories, units sensible, no impossible values).
                        - The figure supports a clear overview (dominant trends/differences/stages) and meaningful comparisons at band 7–9 level.
                        - For PROCESS: steps are in a logical order and describable in the passive; for MAP: two maps of the same place with clearly visible changes.
                        - key_features correctly state what the figure shows (verify against the numbers).""");
            }

            @Override
            public Verdict judge(Object content, VerificationReport report) {
                return QuestionJudge.review(report);
            }
        };
    }

    @Bean
    Blueprint writingTask1GeneralBlueprint(ItemRepository items) {
        return new Blueprint() {
            @Override
            public TaskType type() {
                return TaskType.WRITING_TASK1_GENERAL;
            }

            @Override
            public List<String> bufferVariants() {
                return java.util.Collections.singletonList(null);
            }

            @Override
            public GenerationPlan plan(GenerationRequest request) {
                String letter = request.variant() != null ? request.variant() : leastUsed(items, type(), LETTER_TYPES);
                Map<String, Object> vars = new HashMap<>();
                vars.put("letter_type", letter);
                vars.put("avoid_titles", String.join("; ", items.recentTitles(type(), PageRequest.of(0, 30))));
                return new GenerationPlan("writing-task1-general-generate", vars, null, "Original content", letter);
            }

            @Override
            public String verifyPrompt() {
                return "content-review";
            }

            @Override
            public Map<String, Object> verifyVars(Object content) {
                return ContentReviewSupport.vars("IELTS General Training Writing Task 1 letter prompt", content, """
                        - Real rubric format: situation, "Write a letter to…", "In your letter" + exactly three bullet points, "Write at least 150 words.", "You do NOT need to write any addresses.", "Begin your letter as follows: Dear …,".
                        - The opening ("Dear Sir or Madam," / "Dear Mr …," / "Dear [first name]," equivalent) matches the letter type's register.
                        - The three bullets each require a different function (explain, describe, request, suggest…) and are answerable in ~150–200 words.""");
            }

            @Override
            public Verdict judge(Object content, VerificationReport report) {
                return QuestionJudge.review(report);
            }
        };
    }

    @Bean
    Blueprint writingTask2Blueprint(ItemRepository items, TopicTaxonomy topics) {
        return new Blueprint() {
            @Override
            public TaskType type() {
                return TaskType.WRITING_TASK2;
            }

            @Override
            public List<String> bufferVariants() {
                return java.util.Collections.singletonList(null);
            }

            @Override
            public GenerationPlan plan(GenerationRequest request) {
                String essay = request.variant() != null ? request.variant() : leastUsed(items, type(), ESSAY_TYPES);
                List<String> rotation = topics.rotation();
                String topic = rotation.get(ThreadLocalRandom.current().nextInt(Math.min(3, rotation.size())));
                List<String> subs = topics.topics().get(topic).subtopics();
                Map<String, Object> vars = new HashMap<>();
                vars.put("essay_type", essay);
                vars.put("topic", topic);
                vars.put("subtopic_hint", subs.isEmpty() ? "" : subs.get(ThreadLocalRandom.current().nextInt(subs.size())));
                vars.put("avoid_titles", String.join("; ", items.recentTitles(type(), PageRequest.of(0, 40))));
                return new GenerationPlan("writing-task2-generate", vars, null, "Original content", essay + " / " + topic);
            }

            @Override
            public String verifyPrompt() {
                return "content-review";
            }

            @Override
            public Map<String, Object> verifyVars(Object content) {
                return ContentReviewSupport.vars("IELTS Writing Task 2 essay prompt", content, """
                        - The wording and question form genuinely match the stated essay_type (e.g. DISCUSSION presents two views and asks to discuss both and give an opinion; DOUBLE_QUESTION asks two distinct questions).
                        - Topic is one a real IELTS candidate could discuss from general knowledge; no specialist knowledge, no culturally narrow assumptions, not sensitive/offensive.
                        - The closing rubric is the standard one and asks for at least 250 words.
                        - key_features accurately describe what fully addressing all parts of the task requires.""");
            }

            @Override
            public Verdict judge(Object content, VerificationReport report) {
                return QuestionJudge.review(report);
            }
        };
    }
}
