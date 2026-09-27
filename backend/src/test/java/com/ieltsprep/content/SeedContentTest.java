package com.ieltsprep.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.common.Json;
import com.ieltsprep.listening.ListeningSection;
import com.ieltsprep.marking.QuestionMarker;
import com.ieltsprep.reading.ReadingPassage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every seed file parses into its record, passes ItemValidator, and marks 100% against its own key. */
class SeedContentTest {

    private static final Path SEED = Path.of("../data/seed");
    private final ItemValidator validator = new ItemValidator();

    @Test
    void allSeedFilesAreValidAndCoverEveryTaskType() throws Exception {
        List<String> problems = new ArrayList<>();
        Map<TaskType, Integer> counts = new EnumMap<>(TaskType.class);
        for (Path file : files()) {
            JsonNode doc = Json.MAPPER.readTree(file.toFile());
            List<JsonNode> entries = new ArrayList<>();
            if (doc.isArray()) {
                doc.forEach(entries::add);
            } else {
                entries.add(doc);
            }
            for (JsonNode entry : entries) {
                TaskType type = TaskType.valueOf(entry.get("task_type").asText());
                Object content = Json.MAPPER.treeToValue(entry.get("content"), type.contentType());
                ItemValidator.Report report = validator.validate(type, content);
                if (!report.ok()) {
                    problems.add(file.getFileName() + ": " + report.errors());
                }
                counts.merge(type, 1, Integer::sum);
                selfMark(type, content, file, problems);
            }
        }
        assertThat(problems).isEmpty();
        for (TaskType t : List.of(TaskType.READING_PASSAGE, TaskType.LISTENING_SECTION, TaskType.WRITING_TASK1_ACADEMIC,
                TaskType.WRITING_TASK1_GENERAL, TaskType.WRITING_TASK2, TaskType.SPEAKING_PART1, TaskType.SPEAKING_PART2,
                TaskType.SPEAKING_PART3, TaskType.GRAMMAR_LESSON, TaskType.GRAMMAR_EXERCISE, TaskType.VOCAB_WORD_BANK)) {
            assertThat(counts.getOrDefault(t, 0)).as("seed items for %s", t).isGreaterThanOrEqualTo(3);
        }
        assertThat(counts.get(TaskType.GRAMMAR_DIAGNOSTIC)).isEqualTo(78);
    }

    /** Answering with the key's first answer must score full marks — catches keys the marker can't read. */
    private static void selfMark(TaskType type, Object content, Path file, List<String> problems) {
        List<com.ieltsprep.content.model.Questions.QuestionGroup> groups = switch (type) {
            case READING_PASSAGE -> ((ReadingPassage) content).questionGroups();
            case LISTENING_SECTION -> ((ListeningSection) content).questionGroups();
            default -> List.of();
        };
        if (groups.isEmpty()) {
            return;
        }
        Map<Integer, String> answers = new HashMap<>();
        groups.forEach(g -> g.questions().forEach(q -> answers.put(q.number(), q.answers().getFirst())));
        List<QuestionMarker.QuestionResult> results = QuestionMarker.mark(groups, answers);
        results.stream().filter(r -> !r.correct())
                .forEach(r -> problems.add(file.getFileName() + " Q" + r.number() + ": own key '" + r.given() + "' marked wrong"));
    }

    private static List<Path> files() throws Exception {
        try (Stream<Path> walk = Files.walk(SEED)) {
            return walk.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }
}
