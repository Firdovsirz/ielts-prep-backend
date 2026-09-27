package com.ieltsprep.generation;

import com.ieltsprep.content.model.Questions.DiagramEdge;
import com.ieltsprep.content.model.Questions.DiagramNode;
import com.ieltsprep.content.model.Questions.MapFeature;
import com.ieltsprep.content.model.Questions.MapMarker;
import com.ieltsprep.content.model.Questions.Question;
import com.ieltsprep.content.model.Questions.QuestionGroup;
import com.ieltsprep.content.model.Questions.QuestionOption;
import com.ieltsprep.listening.ListeningSection;
import com.ieltsprep.reading.ReadingPassage;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders an item as the candidate would see it — never including the answer key. Used for blind verification. */
public final class ExamPaperRenderer {

    private static final Pattern GAP = Pattern.compile("\\{\\{(\\d+)}}");

    private ExamPaperRenderer() {}

    public static String reading(ReadingPassage p) {
        StringBuilder sb = new StringBuilder();
        sb.append("READING PASSAGE: ").append(p.title()).append("\n\n");
        for (ReadingPassage.Paragraph para : p.paragraphs()) {
            sb.append(para.label()).append("  ").append(para.text()).append("\n\n");
        }
        sb.append("QUESTIONS\n\n");
        p.questionGroups().forEach(g -> group(sb, g));
        return sb.toString().trim();
    }

    public static String listening(ListeningSection s) {
        StringBuilder sb = new StringBuilder();
        sb.append("LISTENING SECTION ").append(s.section()).append(": ").append(s.title()).append("\n");
        sb.append(s.contextDescription()).append("\n\nSPEAKERS\n");
        s.speakers().forEach(sp -> sb.append(sp.id()).append(" = ").append(sp.name()).append(" (").append(sp.role()).append(")\n"));
        sb.append("\nSCRIPT (line index: speaker: words)\n");
        for (int i = 0; i < s.script().size(); i++) {
            ListeningSection.ScriptLine l = s.script().get(i);
            sb.append(i).append(": ").append(l.speaker()).append(": ").append(l.text()).append("\n");
        }
        sb.append("\nQUESTIONS\n\n");
        s.questionGroups().forEach(g -> group(sb, g));
        return sb.toString().trim();
    }

    static void group(StringBuilder sb, QuestionGroup g) {
        sb.append("[").append(g.questionType().name().toLowerCase(Locale.ROOT)).append("]\n");
        sb.append(g.instructions().trim()).append("\n");
        if (!g.options().isEmpty()) {
            sb.append("\n");
            for (QuestionOption o : g.options()) {
                sb.append("  ").append(o.key()).append("  ").append(o.text()).append("\n");
            }
        }
        if (g.context() != null && !g.context().isBlank()) {
            sb.append("\n").append(gaps(g.context())).append("\n");
        }
        if (!g.table().rows().isEmpty()) {
            sb.append("\nTable: ").append(g.table().title()).append("\n");
            sb.append("| ").append(String.join(" | ", g.table().columns())).append(" |\n");
            for (List<String> row : g.table().rows()) {
                sb.append("| ").append(gaps(String.join(" | ", row))).append(" |\n");
            }
        }
        if (!g.flowSteps().isEmpty()) {
            sb.append("\nFlow-chart:\n");
            for (String step : g.flowSteps()) {
                sb.append("  [ ").append(gaps(step)).append(" ]\n       ↓\n");
            }
        }
        if (!g.diagram().nodes().isEmpty()) {
            sb.append("\nDiagram: ").append(g.diagram().title()).append("\n");
            for (DiagramNode n : g.diagram().nodes()) {
                sb.append("  node ").append(n.id()).append(" at (").append(fmt(n.x())).append(",").append(fmt(n.y())).append("): ")
                        .append(gaps(n.label())).append("\n");
            }
            for (DiagramEdge e : g.diagram().edges()) {
                sb.append("  ").append(e.from()).append(" → ").append(e.to()).append(e.label().isBlank() ? "" : " (" + e.label() + ")").append("\n");
            }
        }
        if (!g.map().features().isEmpty()) {
            sb.append("\nMap: ").append(g.map().title()).append(" (0–100 canvas, x to the right/east, y downwards/south, north at the top; x,y = top-left corner)\n");
            for (MapFeature f : g.map().features()) {
                sb.append("  ").append(f.kind()).append(" '").append(f.label().isBlank() ? "(unlabelled)" : f.label())
                        .append("' x=").append(fmt(f.x())).append(" y=").append(fmt(f.y())).append(" w=").append(fmt(f.w()))
                        .append(" h=").append(fmt(f.h())).append("\n");
            }
            for (MapMarker m : g.map().markers()) {
                sb.append("  marker ").append(m.key()).append(" at (").append(fmt(m.x())).append(",").append(fmt(m.y())).append(")\n");
            }
        }
        sb.append("\n");
        for (Question q : g.questions()) {
            sb.append(q.number()).append(". ");
            if (q.prompt() != null && !q.prompt().isBlank()) {
                sb.append(gaps(q.prompt()));
            } else {
                sb.append("(gap ").append(q.number()).append(" above)");
            }
            sb.append("\n");
            for (QuestionOption o : q.options()) {
                sb.append("     ").append(o.key()).append("  ").append(o.text()).append("\n");
            }
        }
        sb.append("\n");
    }

    private static String gaps(String text) {
        Matcher m = GAP.matcher(text);
        return m.replaceAll("($1) ________");
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
