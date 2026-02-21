package org.knowledgeroot.app.security.config;

import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.security.auth.DatabaseAuthentificationProvider;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityMatrixTestController.class)
@Import(WebSecurityConfig.class)
class RouteAuthorizationMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DatabaseAuthentificationProvider authProvider;

    @MockBean
    private UserContext userContext;

    @Test
    void anonymousShouldBeRedirectedOnProtectedRoutes() throws Exception {
        when(userContext.getUserContext()).thenReturn(role(UserDetails.Role.GUEST));

        mockMvc.perform(get("/page/1"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/file/1"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/demo"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "USER")
    void userRoleShouldHaveReadOnlyApiAccess() throws Exception {
        when(userContext.getUserContext()).thenReturn(role(UserDetails.Role.USER));

        mockMvc.perform(get("/page/1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/file/1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/download/1/test.txt"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/demo"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/user/1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/group/1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/page").with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/file/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminRoleShouldAccessWriteEndpoints() throws Exception {
        when(userContext.getUserContext()).thenReturn(role(UserDetails.Role.ADMIN));

        mockMvc.perform(get("/user/1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/group/1"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/user").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/group").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/page").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/file/1").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/demo"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void csrfShouldStillBeRequiredForWrites() throws Exception {
        when(userContext.getUserContext()).thenReturn(role(UserDetails.Role.ADMIN));

        mockMvc.perform(post("/page"))
                .andExpect(status().isForbidden());
    }

    private UserDetails role(UserDetails.Role role) {
        return UserDetails.builder()
                .userId("1")
                .login("test")
                .email("test@example.org")
                .firstName("Test")
                .surName("User")
                .role(role)
                .language("en")
                .build();
    }
}
