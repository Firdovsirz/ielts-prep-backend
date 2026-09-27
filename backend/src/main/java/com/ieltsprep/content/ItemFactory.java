package com.ieltsprep.content;

import com.ieltsprep.common.Json;
import com.ieltsprep.content.model.Questions;
import com.ieltsprep.content.model.Questions.QuestionGroup;
import com.ieltsprep.grammar.GrammarContent;
import com.ieltsprep.listening.ListeningSection;
import com.ieltsprep.reading.ReadingPassage;
import com.ieltsprep.speaking.SpeakingPrompts;
import com.ieltsprep.vocab.WordBank;
import com.ieltsprep.writing.WritingPrompts;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Builds an {@link Item} row from a typed content document, deriving bucket, title, topic and answer key. */
@Component
public class ItemFactory {

    private final Clock clock;

    public ItemFactory(Clock clock) {
        this.clock = clock;
    }

    public Item create(TaskType type, Object content, String sourceUrl, String licence, ItemOrigin origin) {
        Item item = new Item();
        item.setModule(type.module());
        item.setTaskType(type);
        item.setOrigin(origin);
        item.setSourceUrl(blankToNull(sourceUrl));
        item.setLicence(blankToNull(licence));
        item.setVerificationStatus(VerificationStatus.PENDING);
        item.setCreatedAt(Instant.now(clock));
        apply(item, content);
        return item;
    }

    /** (Re)derives the metadata columns and stores the content + answer key. */
    public void apply(Item item, Object content) {
        TaskType type = item.getTaskType();
        item.setContent(Json.write(content));
        item.setExamType(ExamType.BOTH);
        switch (type) {
            case READING_PASSAGE -> {
                ReadingPassage p = (ReadingPassage) content;
                item.setVariant("P" + p.difficulty());
                item.setDifficulty(p.difficulty());
                item.setCefr(p.cefr());
                item.setTitle(p.title());
                item.setTopic(p.topic());
                item.setExamType(ExamType.ACADEMIC);
                item.setQuestionTypes(types(p.questionGroups()));
                item.setAnswerKey(Json.write(Questions.answerKey(p.questionGroups())));
            }
            case LISTENING_SECTION -> {
                ListeningSection s = (ListeningSection) content;
                item.setVariant("S" + s.section());
                item.setDifficulty(s.section());
                item.setTitle(s.title());
                item.setTopic(s.topic());
                item.setQuestionTypes(types(s.questionGroups()));
                item.setAnswerKey(Json.write(Questions.answerKey(s.questionGroups())));
            }
            case WRITING_TASK1_ACADEMIC -> {
                WritingPrompts.Task1Academic t = (WritingPrompts.Task1Academic) content;
                item.setVariant(t.chartType());
                item.setQuestionTypes(t.chartType());
                item.setTitle(t.figureTitle() == null || t.figureTitle().isBlank() ? t.topic() : t.figureTitle());
                item.setTopic(t.topic());
                item.setExamType(ExamType.ACADEMIC);
            }
            case WRITING_TASK1_GENERAL -> {
                WritingPrompts.Task1General t = (WritingPrompts.Task1General) content;
                item.setVariant(t.letterType());
                item.setQuestionTypes(t.letterType());
                item.setTitle(t.situation());
                item.setTopic(t.topic());
                item.setExamType(ExamType.GENERAL);
            }
            case WRITING_TASK2 -> {
                WritingPrompts.Task2 t = (WritingPrompts.Task2) content;
                item.setVariant(t.essayType());
                item.setQuestionTypes(t.essayType());
                item.setTitle(t.subtopic());
                item.setTopic(t.topic());
            }
            case SPEAKING_PART1 -> {
                SpeakingPrompts.Part1 p = (SpeakingPrompts.Part1) content;
                item.setTitle(p.topics().stream().map(SpeakingPrompts.Part1Topic::topic).collect(Collectors.joining(", ")));
                item.setTopic(p.topics().isEmpty() ? null : p.topics().getFirst().topic());
            }
            case SPEAKING_PART2 -> {
                SpeakingPrompts.Part2 p = (SpeakingPrompts.Part2) content;
                item.setTitle(p.cueCard().prompt());
                item.setTopic(p.topic());
                item.setVariant(p.theme());
            }
            case SPEAKING_PART3 -> {
                SpeakingPrompts.Part3 p = (SpeakingPrompts.Part3) content;
                item.setTitle(p.theme());
                item.setTopic(p.linkedPart2Topic());
                item.setVariant(p.theme());
            }
            case GRAMMAR_LESSON -> {
                GrammarContent.Lesson l = (GrammarContent.Lesson) content;
                item.setVariant(l.area());
                item.setTitle(l.title());
                item.setTopic(l.area());
            }
            case GRAMMAR_EXERCISE -> {
                GrammarContent.ExerciseSet e = (GrammarContent.ExerciseSet) content;
                item.setVariant(e.area());
                item.setQuestionTypes(e.exerciseType());
                item.setTitle(e.title());
                item.setTopic(e.area());
                Map<String, List<String>> key = new LinkedHashMap<>();
                e.items().forEach(i -> key.put(i.id(), i.acceptedAnswers()));
                item.setAnswerKey(Json.write(key));
            }
            case GRAMMAR_DIAGNOSTIC -> {
                GrammarContent.DiagnosticQuestion q = (GrammarContent.DiagnosticQuestion) content;
                item.setVariant(q.area());
                item.setDifficulty(q.level());
                item.setTitle(q.id());
                item.setTopic(q.area());
                item.setAnswerKey(Json.write(Map.of(q.id(), q.answer())));
            }
            case GRAMMAR_ERROR_DRILL -> {
                GrammarContent.ErrorDrill d = (GrammarContent.ErrorDrill) content;
                item.setVariant(d.subtype());
                item.setTitle(d.rule());
                item.setTopic(d.area());
            }
            case VOCAB_WORD_BANK -> {
                WordBank b = (WordBank) content;
                item.setVariant(b.topic());
                item.setTitle(b.topic());
                item.setTopic(b.topic());
            }
        }
        item.setTitle(truncate(item.getTitle(), 512));
        item.setTopic(truncate(item.getTopic(), 255));
        item.setVariant(truncate(item.getVariant(), 64));
    }

    private static String types(List<QuestionGroup> groups) {
        return groups.stream().map(g -> g.questionType().name()).distinct().collect(Collectors.joining(","));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
