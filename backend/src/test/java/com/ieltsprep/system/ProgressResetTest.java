package com.ieltsprep.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ieltsprep.claude.ClaudeService;
import com.ieltsprep.content.ItemRepository;
import com.ieltsprep.session.PracticeSessionRepository;
import com.ieltsprep.support.IntegrationTest;
import com.ieltsprep.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ProgressResetTest {

    @Autowired MockMvc mvc;
    @Autowired PracticeSessionRepository sessions;
    @Autowired ItemRepository items;
    @MockitoBean ClaudeService claude;

    @Test
    void resetNeedsConfirmationAndKeepsContentAndLogin() throws Exception {
        String auth = TestAuth.bearer(mvc);
        mvc.perform(post("/api/reading/sessions").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"PRACTICE\",\"scope\":\"PASSAGE\"}")).andExpect(status().isOk());
        assertThat(sessions.count()).isPositive();
        long content = items.count();

        mvc.perform(post("/api/system/reset-progress").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirm\":\"yes\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/system/reset-progress").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirm\":\"RESET\"}")).andExpect(status().isOk());

        assertThat(sessions.count()).isZero();
        assertThat(items.count()).isEqualTo(content);
        assertThat(TestAuth.bearer(mvc)).startsWith("Bearer ");
    }
}
