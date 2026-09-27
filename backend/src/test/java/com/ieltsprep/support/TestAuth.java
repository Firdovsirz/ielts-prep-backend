package com.ieltsprep.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.common.Json;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public final class TestAuth {

    private TestAuth() {}

    /** Logs in as the test admin (application-test.yml) and returns the bearer header value. */
    public static String bearer(MockMvc mvc) throws Exception {
        String body = mvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"tester@example.com\",\"password\":\"test-password-123\"}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = Json.tree(body);
        return "Bearer " + node.get("token").asText();
    }
}
