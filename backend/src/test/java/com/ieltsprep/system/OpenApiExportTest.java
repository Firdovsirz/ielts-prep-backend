package com.ieltsprep.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.Json;
import com.ieltsprep.support.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Writes the OpenAPI document to docs/api/openapi.json on every test run. The frontend generates its TypeScript API
 * types from that file (npm run gen:api), so front and back cannot drift silently.
 */
@IntegrationTest
class OpenApiExportTest {

    @Autowired MockMvc mvc;
    @MockitoBean ClaudeService claude;

    @Test
    void exportsSpec() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        JsonNode spec = Json.tree(body);
        assertThat(spec.path("paths").has("/api/reading/sessions")).isTrue();
        Path out = Path.of("../docs/api/openapi.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, Json.pretty(spec) + "\n");
    }
}
