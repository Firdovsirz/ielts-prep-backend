package com.ieltsprep.listening;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ieltsprep.content.model.Questions.QuestionGroup;
import java.util.List;
import java.util.stream.Collectors;

/** Mirrors prompts/schemas/listening-section.schema.json. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ListeningSection(
        int section,
        String title,
        String topic,
        String contextDescription,
        List<Speaker> speakers,
        List<ScriptLine> script,
        List<ListeningPart> parts,
        List<QuestionGroup> questionGroups) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Speaker(String id, String name, String gender, String accent, String role) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ScriptLine(String speaker, String text) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ListeningPart(int questionsFrom, int questionsTo, int startLine) {}

    /** Questions only — the script is withheld until the section has been submitted (exam fidelity). */
    public ListeningSection withoutAnswers(boolean includeScript) {
        return new ListeningSection(section, title, topic, contextDescription, speakers,
                includeScript ? script : List.of(), parts,
                questionGroups.stream().map(QuestionGroup::withoutAnswers).toList());
    }

    public String fullScript() {
        return script.stream().map(l -> l.speaker() + ": " + l.text()).collect(Collectors.joining("\n"));
    }
}
