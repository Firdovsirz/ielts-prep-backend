package com.ieltsprep.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.anthropic.models.messages.TextBlockParam;
import com.ieltsprep.config.AppProperties;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Every prompt file parses, names an existing schema and existing context files, and renders into SDK params. */
class PromptCatalogueTest {

    private final AppProperties app = new AppProperties("./../data", "./../prompts", null, false, null, null, null, null,
            null, null, null, null, null);
    private final PromptRepository prompts = new PromptRepository(app);
    private final SchemaRepository schemas = new SchemaRepository(app);
    private final ReferenceLibrary refs = new ReferenceLibrary(app);

    PromptCatalogueTest() {
        prompts.load();
        schemas.load();
    }

    @Test
    void allPromptsAreWellFormed() {
        assertThat(prompts.all()).isNotEmpty();
        prompts.all().values().forEach(p -> {
            assertThat(p.system()).as(p.name() + " system").isNotBlank();
            assertThat(p.user()).as(p.name() + " user").isNotBlank();
            if (p.schema() != null) {
                assertThat(schemas.has(p.schema())).as(p.name() + " schema " + p.schema()).isTrue();
            }
            p.context().forEach(f -> assertThat(refs.exists(f)).as(p.name() + " context " + f).isTrue());
            assertThat(p.system()).as(p.name() + ": system text must not contain per-call variables")
                    .doesNotContainPattern("\\{\\{\\s*(?!n\\s*}})[a-zA-Z_][a-zA-Z0-9_]*\\s*}}"); // {{n}} is a literal gap marker
        });
    }

    @Test
    void templatesKeepGapMarkersAndSubstituteVariables() {
        String out = PromptTemplate.render("Write {{topic}} with gaps like {{7}} and {{n}}", Map.of("topic", "bees"));
        assertThat(out).isEqualTo("Write bees with gaps like {{7}} and {{n}}");
    }

    @Test
    void preparedRequestPutsReferencesFirstAndCachesTheStaticPrefix() {
        ClaudeProperties props = new ClaudeProperties("sk-test", null, 60, 1, 100, BigDecimal.ONE, 0.7, "5m",
                Map.of("generation", "claude-sonnet-5", "verification", "claude-sonnet-5", "grading", "claude-sonnet-5",
                        "coaching", "claude-sonnet-5", "examiner", "claude-sonnet-5", "fast", "claude-haiku-4-5"),
                Map.of("generation", "medium"), Map.of("generation", 32000L), Map.of());
        ClaudeService service = new ClaudeService(props, prompts, schemas, refs, mock(SpendGuard.class), mock(UsageLogger.class));

        PreparedRequest req = service.prepare(ClaudeCall.of("reading-generate", Map.of("passage_number", 2,
                "question_plan", "- Questions 1–13: …", "feedback", ""), Object.class));

        assertThat(req.model()).isEqualTo("claude-sonnet-5");
        assertThat(req.maxTokens()).isEqualTo(32000L);
        assertThat(req.system().getFirst().text()).startsWith("<reference path=\"data/templates/reading-academic.json\">");
        TextBlockParam last = req.system().getLast();
        assertThat(last.cacheControl()).isPresent();
        req.system().subList(0, req.system().size() - 1).forEach(b -> assertThat(b.cacheControl()).isEmpty());
        assertThat(req.outputConfig().format()).isPresent();
        assertThat(req.toMessageParams().messages().getLast().content().string()).hasValueSatisfying(
                s -> assertThat(s).contains("Reading Passage 2"));
    }
}
