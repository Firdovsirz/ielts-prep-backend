package com.ieltsprep.support;

import com.ieltsprep.grading.GradingModels.CriterionBand;
import com.ieltsprep.grading.GradingModels.TaggedError;
import com.ieltsprep.grading.GradingModels.VocabUpgrade;
import com.ieltsprep.grading.GradingModels.WritingGrade;
import java.util.List;

/** Shared test data: a band-6 essay with deliberate errors and the grade a mocked ClaudeService returns for it. */
public final class Fixtures {

    private Fixtures() {}

    public static final String ESSAY = "Some people believe that working four days is better. In my opinion, this have many benefit "
            + "for employees and also for companies. Firstly, the workers is more rested and they are more productive.";

    public static WritingGrade writingGrade() {
        return new WritingGrade(
                List.of(new CriterionBand("TASK_RESPONSE", 6, "Addresses the prompt; \"many benefit\" is underdeveloped.", List.of(), List.of()),
                        new CriterionBand("COHERENCE_COHESION", 7, "Logical.", List.of(), List.of()),
                        new CriterionBand("LEXICAL_RESOURCE", 6, "Adequate.", List.of(), List.of()),
                        new CriterionBand("GRAMMATICAL_RANGE_ACCURACY", 6, "Frequent agreement errors.", List.of(), List.of())),
                List.of(new TaggedError("grammar", "Subject-Verb Agreement", "this have", "this has", "Singular subject."),
                        new TaggedError("grammar", "plural_form", "many benefit", "many benefits", "Countable plural."),
                        new TaggedError("vocabulary", "word_choice", "better", "more beneficial", "Precision.")),
                List.of("Develop each idea with an example", "Fix agreement", "Vary linkers"),
                List.of(new VocabUpgrade("many benefit", "numerous advantages", "This has numerous advantages for employees.")),
                "Under length.", "Solid band 6.", "A model answer…");
    }
}
