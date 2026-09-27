package com.ieltsprep.grammar;

import java.util.List;

/** Plain-text rendering of exercises (without answers) for the blind verifier and the answer checker. */
final class ExerciseText {

    private ExerciseText() {}

    static String set(GrammarContent.ExerciseSet set) {
        StringBuilder sb = new StringBuilder();
        sb.append("Exercise: ").append(set.title()).append(" (").append(set.exerciseType()).append(", area ").append(set.area()).append(")\n");
        sb.append("Instructions: ").append(set.instructions()).append("\n\n");
        items(sb, set.items());
        return sb.toString();
    }

    static String drill(GrammarContent.ErrorDrill d) {
        StringBuilder sb = new StringBuilder();
        sb.append("Error drill for subtype ").append(d.subtype()).append(" (area ").append(d.area()).append(")\n");
        sb.append("Rule given to the candidate: ").append(d.rule()).append("\n\n");
        sb.append("Item own [ERROR_CORRECTION, EXACT_OR_AI]: Correct this sentence: ").append(d.ownSentence()).append("\n\n");
        items(sb, d.freshItems());
        return sb.toString();
    }

    private static void items(StringBuilder sb, List<GrammarContent.ExerciseItem> items) {
        for (GrammarContent.ExerciseItem i : items) {
            sb.append("Item ").append(i.id()).append(" [").append(i.checkMode()).append("]: ").append(i.prompt()).append("\n");
            if (!i.options().isEmpty()) {
                sb.append("  Options: ").append(String.join(" | ", i.options())).append("\n");
            }
        }
    }
}
