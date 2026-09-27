package com.ieltsprep.generation;

import java.util.List;
import java.util.stream.Collectors;

/** A fixed question layout (types, ranges, rubric limits) handed to the generator for exam-faithful variety. */
public record QuestionPlan(List<Segment> segments) {

    public record Segment(int from, int to, String type, String rubric) {}

    public static QuestionPlan of(Segment... segments) {
        return new QuestionPlan(List.of(segments));
    }

    public static Segment seg(int from, int to, String type, String rubric) {
        return new Segment(from, to, type, rubric);
    }

    public int count() {
        return segments.getLast().to();
    }

    public String describe() {
        return segments.stream()
                .map(s -> "- Questions " + s.from() + "–" + s.to() + ": " + s.type() + (s.rubric().isBlank() ? "" : " — " + s.rubric()))
                .collect(Collectors.joining("\n"));
    }
}
