package com.ieltsprep.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.common.ApiException;
import com.ieltsprep.common.Json;
import com.ieltsprep.support.IntegrationTest;
import com.ieltsprep.support.TestAuth;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class AccountTest {

    static final String EMAIL = "tester@example.com";
    static final String PASSWORD = "test-password-123";

    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;
    @Autowired AdminBootstrap bootstrap;
    @MockitoBean ClaudeService claude;

    @AfterEach
    void restoreTheTestLogin() {
        auth.resetAdmin(EMAIL, PASSWORD); // other test classes log in with it
    }

    ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(Json.write(Map.of("email", email, "password", password))));
    }

    ResultActions updateAccount(String bearer, Map<String, String> body) throws Exception {
        return mvc.perform(put("/api/auth/account").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(Json.write(body)));
    }

    @Test
    void theSignedInUserCanChangeTheirEmailAndPassword() throws Exception {
        String bearer = TestAuth.bearer(mvc);
        updateAccount(bearer, Map.of("currentPassword", "wrong-password", "email", "new@example.com")).andExpect(status().isBadRequest());
        updateAccount(bearer, Map.of("currentPassword", PASSWORD, "email", "not-an-email")).andExpect(status().isBadRequest());
        updateAccount(bearer, Map.of("currentPassword", PASSWORD, "newPassword", "short")).andExpect(status().isBadRequest());

        JsonNode res = Json.tree(updateAccount(bearer, Map.of("currentPassword", PASSWORD, "email", "new.admin@example.com",
                "newPassword", "a-brand-new-pass")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(res.get("email").asText()).isEqualTo("new.admin@example.com");

        login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
        login("NEW.admin@example.com", "a-brand-new-pass").andExpect(status().isOk());
        String me = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + res.get("token").asText()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Json.tree(me).get("email").asText()).isEqualTo("new.admin@example.com");
    }

    @Test
    void theOperatorCanResetALostLoginAndRestartsDoNotAddASecondAdmin() throws Exception {
        long before = users.count();
        assertThat(auth.resetAdmin(" reset@example.com ", "reset-password-1")).isEqualTo("reset@example.com");
        assertThat(users.count()).isEqualTo(before);
        login("reset@example.com", "reset-password-1").andExpect(status().isOk());
        login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());

        bootstrap.run(null); // ADMIN_EMAIL in the test config is still tester@example.com
        assertThat(users.count()).as("no second admin from a stale ADMIN_EMAIL").isEqualTo(before);

        assertThatThrownBy(() -> auth.resetAdmin("reset@example.com", "short")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> auth.resetAdmin("no-at-sign", "long-enough-pass")).isInstanceOf(ApiException.class);
    }
}
