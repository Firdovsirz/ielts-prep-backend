package com.ieltsprep.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.ApiException;
import com.ieltsprep.common.Json;
import com.ieltsprep.support.IntegrationTest;
import com.ieltsprep.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ApiKeyEndpointTest {

    @Autowired MockMvc mvc;
    @MockitoBean ClaudeService claude;

    @AfterEach
    void cleanUp() throws Exception {
        mvc.perform(delete("/api/system/api-key").header("Authorization", TestAuth.bearer(mvc)));
    }

    @Test
    void theKeyCanBeEnteredInTheAppAndIsNeverReturned() throws Exception {
        String auth = TestAuth.bearer(mvc);
        mvc.perform(put("/api/system/api-key").contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/system/api-key").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"not-a-key\"}")).andExpect(status().isBadRequest());

        String key = "sk-ant-api03-" + "x".repeat(40) + "Wxyz";
        String body = mvc.perform(put("/api/system/api-key").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\" " + key + " \"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode saved = Json.tree(body);
        assertThat(saved.get("configured").asBoolean()).isTrue();
        assertThat(saved.get("source").asText()).isEqualTo("APP");
        assertThat(saved.get("hint").asText()).isEqualTo("…Wxyz");
        assertThat(body).doesNotContain(key);

        String status = mvc.perform(get("/api/system/status").header("Authorization", auth)).andReturn().getResponse().getContentAsString();
        assertThat(Json.tree(status).get("apiKeySource").asText()).isEqualTo("APP");
        assertThat(status).doesNotContain(key);

        JsonNode cleared = Json.tree(mvc.perform(delete("/api/system/api-key").header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(cleared.get("source").asText()).isNotEqualTo("APP");
    }

    @Test
    void aKeyAnthropicRejectsIsNotSaved() throws Exception {
        String auth = TestAuth.bearer(mvc);
        doThrow(ApiException.badRequest("Anthropic rejected this key")).when(claude).verifyKey(anyString());
        mvc.perform(put("/api/system/api-key").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"sk-ant-api03-" + "y".repeat(40) + "\"}")).andExpect(status().isBadRequest());
        assertThat(Json.tree(mvc.perform(get("/api/system/api-key").header("Authorization", auth)).andReturn().getResponse()
                .getContentAsString()).get("source").asText()).isNotEqualTo("APP");
    }
}
