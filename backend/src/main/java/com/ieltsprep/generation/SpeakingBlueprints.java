package com.ieltsprep.generation;

import com.ieltsprep.common.Json;
import com.ieltsprep.content.Item;
import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.content.VerificationStatus;
import com.ieltsprep.speaking.SpeakingPrompts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;

/** Blueprints for Speaking Part 1 question sets, Part 2 cue cards and linked Part 3 discussions. */
@Configuration
public class SpeakingBlueprints {

    private static final String REVIEW = """
            - Examiner phrasing is natural and matches the real test (short, direct, no multi-part rambling questions).
            - Difficulty and abstraction match the part (Part 1 personal/familiar; Part 2 describable experience with three bullets and "and explain…"; Part 3 abstract, societal, speculative).
            - Nothing culturally narrow, sensitive, or requiring specialist knowledge.""";

    @Bean
    Blueprint speakingPart1Blueprint(ItemRepository items) {
        return reviewBlueprint(TaskType.SPEAKING_PART1, "speaking-part1-generate", "IELTS Speaking Part 1 question set", items);
    }

    @Bean
    Blueprint speakingPart2Blueprint(ItemRepository items) {
        return reviewBlueprint(TaskType.SPEAKING_PART2, "speaking-part2-generate", "IELTS Speaking Part 2 cue card", items);
    }

    @Bean
    Blueprint speakingPart3Blueprint(ItemRepository items) {
        return new Blueprint() {
            @Override
            public TaskType type() {
                return TaskType.SPEAKING_PART3;
            }

            @Override
            public List<String> bufferVariants() {
                return java.util.Collections.singletonList(null);
            }

            @Override
            public GenerationPlan plan(GenerationRequest request) {
                List<Item> cards = items.findByTaskTypeAndVerificationStatusOrderByIdAsc(TaskType.SPEAKING_PART2, VerificationStatus.VERIFIED);
                Set<String> covered = items.findByTaskTypeAndVerificationStatusOrderByIdAsc(TaskType.SPEAKING_PART3, VerificationStatus.VERIFIED)
                        .stream().map(Item::getTopic).collect(Collectors.toSet());
                List<Item> uncovered = cards.stream().filter(c -> !covered.contains(c.getTopic())).toList();
                List<Item> pool = uncovered.isEmpty() ? cards : uncovered;
                Map<String, Object> vars = new HashMap<>();
                if (!pool.isEmpty()) {
                    Item card = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                    SpeakingPrompts.Part2 p2 = card.contentAs(SpeakingPrompts.Part2.class);
                    vars.put("cue_card", Json.pretty(p2));
                    vars.put("part2_topic", p2.topic());
                    vars.put("theme", p2.theme());
                } else {
                    vars.put("cue_card", "(none — choose a common Part 2 theme)");
                    vars.put("part2_topic", "");
                    vars.put("theme", "");
                }
                return new GenerationPlan("speaking-part3-generate", vars, null, "Original content", "Part 3: " + vars.get("theme"));
            }

            @Override
            public String verifyPrompt() {
                return "content-review";
            }

            @Override
            public Map<String, Object> verifyVars(Object content) {
                return ContentReviewSupport.vars("IELTS Speaking Part 3 discussion linked to a Part 2 cue card", content, REVIEW);
            }

            @Override
            public Verdict judge(Object content, VerificationReport report) {
                return QuestionJudge.review(report);
            }
        };
    }

    private static Blueprint reviewBlueprint(TaskType type, String prompt, String kind, ItemRepository items) {
        return new Blueprint() {
            @Override
            public TaskType type() {
                return type;
            }

            @Override
            public List<String> bufferVariants() {
                return java.util.Collections.singletonList(null);
            }

            @Override
            public GenerationPlan plan(GenerationRequest request) {
                Map<String, Object> vars = new HashMap<>();
                vars.put("avoid_titles", String.join("; ", items.recentTitles(type, PageRequest.of(0, 40))));
                return new GenerationPlan(prompt, vars, null, "Original content", kind);
            }

            @Override
            public String verifyPrompt() {
                return "content-review";
            }

            @Override
            public Map<String, Object> verifyVars(Object content) {
                return ContentReviewSupport.vars(kind, content, REVIEW);
            }

            @Override
            public Verdict judge(Object content, VerificationReport report) {
                return QuestionJudge.review(report);
            }
        };
    }
}
