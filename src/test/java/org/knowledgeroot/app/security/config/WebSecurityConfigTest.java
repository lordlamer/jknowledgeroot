package org.knowledgeroot.app.security.config;

import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.security.auth.DatabaseAuthentificationProvider;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = TestPostController.class)
@Import({WebSecurityConfig.class, TestPostController.class})
class WebSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseAuthentificationProvider authProvider;

    @MockitoBean
    private UserContext userContext;

    @Test
    @WithMockUser
    void postWithoutCsrfShouldReturnForbidden() throws Exception {
        when(userContext.getUserContext()).thenReturn(testUser());
        mockMvc.perform(post("/dummy"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void postWithCsrfShouldSucceed() throws Exception {
        when(userContext.getUserContext()).thenReturn(testUser());
        mockMvc.perform(post("/dummy").with(csrf()))
                .andExpect(status().isOk());
    }

    private UserDetails testUser() {
        return UserDetails.builder()
                .userId("1")
                .login("test")
                .email("test@example.org")
                .firstName("Test")
                .surName("User")
                .role(UserDetails.Role.USER)
                .language("en")
                .build();
    }

}
