package com.ieltsprep.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.content.TaskType;
import com.ieltsprep.content.VerificationStatus;
import com.ieltsprep.support.IntegrationTest;
import com.ieltsprep.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ApplicationSmokeTest {

    @Autowired MockMvc mvc;
    @Autowired ItemRepository items;
    @MockitoBean ClaudeService claude;

    @Test
    void seedContentIsLoadedAndVerified() {
        assertThat(items.countByTaskTypeAndVerificationStatus(TaskType.READING_PASSAGE, VerificationStatus.VERIFIED)).isGreaterThanOrEqualTo(9);
        assertThat(items.countByTaskTypeAndVerificationStatus(TaskType.LISTENING_SECTION, VerificationStatus.VERIFIED)).isGreaterThanOrEqualTo(12);
        assertThat(items.countByTaskTypeAndVerificationStatus(TaskType.GRAMMAR_DIAGNOSTIC, VerificationStatus.VERIFIED)).isEqualTo(78);
    }

    @Test
    void apiRequiresLogin() throws Exception {
        mvc.perform(get("/api/system/status")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"tester@example.com\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void bootstrapAdminCanLogInAndReadStatus() throws Exception {
        String auth = TestAuth.bearer(mvc);
        mvc.perform(get("/api/auth/me").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("tester@example.com"));
        mvc.perform(get("/api/system/status").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKeyConfigured").value(false))
                .andExpect(jsonPath("$.models.generation").value("claude-sonnet-5"));
    }
}
