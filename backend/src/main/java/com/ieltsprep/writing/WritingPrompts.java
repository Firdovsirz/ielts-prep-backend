package com.ieltsprep.writing;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** Mirrors the writing-task1-academic, writing-task1-general and writing-task2 schemas. */
public final class WritingPrompts {

    private WritingPrompts() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ChartSeries(String name, List<Double> values) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ProcessStep(int step, String label, String description) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MapFigureFeature(String label, String kind, double x, double y, double w, double h) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MapFigure(String title, List<MapFigureFeature> features) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Task1Academic(
            String chartType,
            String topic,
            String prompt,
            String figureTitle,
            String units,
            String xLabel,
            String yLabel,
            List<String> categories,
            List<ChartSeries> series,
            List<ProcessStep> processSteps,
            boolean processIsCycle,
            List<MapFigure> maps,
            List<String> keyFeatures) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Task1General(
            String letterType,
            String topic,
            String situation,
            String recipient,
            List<String> bulletPoints,
            String prompt,
            List<String> keyFeatures) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Task2(
            String essayType,
            String topic,
            String subtopic,
            String statement,
            String question,
            String prompt,
            List<String> keyFeatures) {}
}
