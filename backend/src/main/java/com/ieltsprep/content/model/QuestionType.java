package com.ieltsprep.content.model;

import java.util.EnumSet;
import java.util.Set;

/** All Reading (14 official types) and Listening question types. */
public enum QuestionType {
    MULTIPLE_CHOICE("Multiple choice"),
    MULTIPLE_CHOICE_MULTI("Multiple choice (choose two/three)"),
    TRUE_FALSE_NOT_GIVEN("Identifying information (True/False/Not Given)"),
    YES_NO_NOT_GIVEN("Identifying views/claims (Yes/No/Not Given)"),
    MATCHING_INFORMATION("Matching information"),
    MATCHING_HEADINGS("Matching headings"),
    MATCHING_FEATURES("Matching features"),
    MATCHING_SENTENCE_ENDINGS("Matching sentence endings"),
    SENTENCE_COMPLETION("Sentence completion"),
    SUMMARY_COMPLETION("Summary completion"),
    NOTE_COMPLETION("Note completion"),
    TABLE_COMPLETION("Table completion"),
    FLOW_CHART_COMPLETION("Flow-chart completion"),
    DIAGRAM_LABEL_COMPLETION("Diagram label completion"),
    SHORT_ANSWER("Short-answer questions"),
    FORM_COMPLETION("Form completion"),
    MAP_LABELLING("Plan/map/diagram labelling"),
    MATCHING("Matching");

    private static final Set<QuestionType> CHOICE = EnumSet.of(MULTIPLE_CHOICE, MULTIPLE_CHOICE_MULTI,
            TRUE_FALSE_NOT_GIVEN, YES_NO_NOT_GIVEN, MATCHING_INFORMATION, MATCHING_HEADINGS, MATCHING_FEATURES,
            MATCHING_SENTENCE_ENDINGS, MATCHING, MAP_LABELLING);

    /** Types whose questions must follow the order of the passage/script. */
    private static final Set<QuestionType> ORDERED = EnumSet.of(TRUE_FALSE_NOT_GIVEN, YES_NO_NOT_GIVEN,
            MULTIPLE_CHOICE, SENTENCE_COMPLETION, SHORT_ANSWER);

    private final String label;

    QuestionType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isChoice() {
        return CHOICE.contains(this);
    }

    public boolean followsTextOrder() {
        return ORDERED.contains(this);
    }
}
