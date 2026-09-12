package org.knowledgeroot.app.security.user.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.knowledgeroot.app.security.auth.DatabaseAuthentificationProvider;
import org.knowledgeroot.app.security.auth.PasswordService;
import org.knowledgeroot.app.security.config.WebSecurityConfig;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.user.api.controller.UserRestController;
import org.knowledgeroot.app.security.user.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AdminController.class, UserRestController.class})
@Import({WebSecurityConfig.class, PasswordService.class})
class PasswordAdministrationWebTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordService passwords;
    @Autowired ObjectMapper json;
    @MockitoBean UserDao users;
    @MockitoBean GroupDao groups;
    @MockitoBean UserContext context;
    @MockitoBean DatabaseAuthentificationProvider provider;
    private User existing;

    @BeforeEach
    void setUp() {
        when(context.getUserContext()).thenReturn(UserDetails.builder().userId("1").login("admin").role(UserDetails.Role.ADMIN).build());
        existing = User.builder().id(new UserId(42)).login("alice").password("existing-verifier").build();
        when(users.findById(new UserId(42))).thenReturn(existing);
        doAnswer(call -> { ((User) call.getArgument(0)).setId(new UserId(43)); return null; }).when(users).createUser(any());
    }

    @Test
    void uiAndApiPreservePasswordWhitespaceAndUseModernHashes() throws Exception {
        String password = "  a-shared-passphrase  ";
        mvc.perform(post("/admin/users").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("login", "alice").param("password", password)).andExpect(status().is3xxRedirection());
        mvc.perform(post("/user").with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("login", "bob", "password", password))))
                .andExpect(status().isCreated());
        var capture = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users, times(2)).createUser(capture.capture());
        for (User saved : capture.getAllValues()) {
            assertTrue(saved.getPassword().startsWith(PasswordService.PREFIX));
            assertTrue(passwords.matches(password, saved.getPassword()));
            assertFalse(passwords.matches(password.trim(), saved.getPassword()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "                "})
    void invalidNewPasswordsAreRejectedByBothInterfacesBeforeWrites(String password) throws Exception {
        mvc.perform(post("/admin/users").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("login", "alice").param("password", password)).andExpect(status().isBadRequest());
        mvc.perform(post("/user").with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("login", "bob", "password", password))))
                .andExpect(status().isBadRequest());
        verify(users, never()).createUser(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "***"})
    void unchangedPasswordMarkersHaveTheSameMeaningInUiAndApi(String marker) throws Exception {
        mvc.perform(post("/admin/users/42").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("password", marker)).andExpect(status().is3xxRedirection());
        mvc.perform(put("/user/42").with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("id", 42, "password", marker))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist());
        var capture = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users, times(2)).updateUser(capture.capture());
        capture.getAllValues().forEach(saved -> assertEquals("existing-verifier", saved.getPassword()));
    }

    @Test
    void passwordUpdateNeverEchoesPlaintextOrVerifier() throws Exception {
        String password = "replacement-passphrase";
        mvc.perform(put("/user/42").with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("id", 42, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist());
        verify(users).updateUser(argThat(saved -> passwords.matches(password, saved.getPassword())));
    }

    @Test
    void invalidReplacementDoesNotChangeTheLoadedUser() throws Exception {
        mvc.perform(post("/admin/users/42").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("login", "changed").param("password", "short"))
                .andExpect(status().isBadRequest());
        assertEquals("alice", existing.getLogin());
        assertEquals("existing-verifier", existing.getPassword());
        verify(users, never()).updateUser(any());
    }
}
