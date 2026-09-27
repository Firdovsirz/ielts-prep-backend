package com.ieltsprep.reading;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ieltsprep.content.model.Questions.QuestionGroup;
import java.util.List;
import java.util.stream.Collectors;

/** Mirrors prompts/schemas/reading-passage.schema.json. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ReadingPassage(
        String title,
        String topic,
        int difficulty,
        String cefr,
        List<Paragraph> paragraphs,
        List<QuestionGroup> questionGroups) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Paragraph(String label, String text) {}

    public ReadingPassage withoutAnswers() {
        return new ReadingPassage(title, topic, difficulty, cefr, paragraphs,
                questionGroups.stream().map(QuestionGroup::withoutAnswers).toList());
    }

    public String fullText() {
        return paragraphs.stream().map(Paragraph::text).collect(Collectors.joining("\n\n"));
    }

    public int wordCount() {
        String text = fullText().trim();
        return text.isEmpty() ? 0 : text.split("\\s+").length;
    }
}
