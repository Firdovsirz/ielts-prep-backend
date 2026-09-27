package com.ieltsprep.writing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.common.Json;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The graders' subtype enum must list exactly the taxonomy's subtypes, so every tagged error maps to an area. */
class GradingSchemaTaxonomyTest {

    @Test
    void subtypeEnumsMatchTaxonomy() throws Exception {
        JsonNode taxonomy = Json.MAPPER.readTree(Path.of("../data/descriptors/grammar-areas.json").toFile());
        List<String> expected = new ArrayList<>();
        taxonomy.get("areas").forEach(a -> a.get("subtypes").forEach(s -> expected.add(s.asText())));
        taxonomy.get("non_grammar_subtypes").forEach(list -> list.forEach(s -> expected.add(s.asText())));
        for (String schema : List.of("writing-grade", "speaking-grade")) {
            JsonNode enumNode = Json.MAPPER.readTree(Path.of("../prompts/schemas/" + schema + ".schema.json").toFile())
                    .at("/properties/errors/items/properties/subtype/enum");
            List<String> actual = new ArrayList<>();
            enumNode.forEach(n -> actual.add(n.asText()));
            assertThat(actual).as(schema).containsExactlyInAnyOrderElementsOf(expected);
        }
    }
}
