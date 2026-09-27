package com.ieltsprep.content;

import com.ieltsprep.grammar.GrammarContent;
import com.ieltsprep.listening.ListeningSection;
import com.ieltsprep.reading.ReadingPassage;
import com.ieltsprep.speaking.SpeakingPrompts;
import com.ieltsprep.vocab.WordBank;
import com.ieltsprep.writing.WritingPrompts;

/** Generated/seeded item types: module, output schema, and the record the JSON maps to. */
public enum TaskType {
    READING_PASSAGE(Skill.READING, "reading-passage", ReadingPassage.class),
    LISTENING_SECTION(Skill.LISTENING, "listening-section", ListeningSection.class),
    WRITING_TASK1_ACADEMIC(Skill.WRITING, "writing-task1-academic", WritingPrompts.Task1Academic.class),
    WRITING_TASK1_GENERAL(Skill.WRITING, "writing-task1-general", WritingPrompts.Task1General.class),
    WRITING_TASK2(Skill.WRITING, "writing-task2", WritingPrompts.Task2.class),
    SPEAKING_PART1(Skill.SPEAKING, "speaking-part1", SpeakingPrompts.Part1.class),
    SPEAKING_PART2(Skill.SPEAKING, "speaking-part2", SpeakingPrompts.Part2.class),
    SPEAKING_PART3(Skill.SPEAKING, "speaking-part3", SpeakingPrompts.Part3.class),
    GRAMMAR_LESSON(Skill.GRAMMAR, "grammar-lesson", GrammarContent.Lesson.class),
    GRAMMAR_EXERCISE(Skill.GRAMMAR, "grammar-exercise-set", GrammarContent.ExerciseSet.class),
    GRAMMAR_DIAGNOSTIC(Skill.GRAMMAR, "grammar-diagnostic-question", GrammarContent.DiagnosticQuestion.class),
    GRAMMAR_ERROR_DRILL(Skill.GRAMMAR, "grammar-error-drill", GrammarContent.ErrorDrill.class),
    VOCAB_WORD_BANK(Skill.VOCAB, "vocab-word-bank", WordBank.class);

    private final Skill module;
    private final String schema;
    private final Class<?> contentType;

    TaskType(Skill module, String schema, Class<?> contentType) {
        this.module = module;
        this.schema = schema;
        this.contentType = contentType;
    }

    public Skill module() {
        return module;
    }

    public String schema() {
        return schema;
    }

    public Class<?> contentType() {
        return contentType;
    }
}
