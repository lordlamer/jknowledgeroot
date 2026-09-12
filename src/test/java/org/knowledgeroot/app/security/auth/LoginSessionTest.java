package org.knowledgeroot.app.security.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.knowledgeroot.app.security.config.WebSecurityConfig;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserContextDao;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.context.domain.UserNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessionTestController.class)
@Import({WebSecurityConfig.class, DatabaseAuthentificationProvider.class, UserContext.class, PasswordService.class})
class LoginSessionTest {
    private static final String PASSWORD = "correct-password";
    private static final String HASH = PasswordHasher.hash(PASSWORD, PasswordHasher.HASH_METHOD.SHA256, 1000);

    @Autowired private MockMvc mvc;
    @MockitoBean private CredentialRepository credentials;
    @MockitoBean private LoginAttemptLimiter limiter;
    @MockitoBean private UserContextDao accounts;

    @BeforeEach
    void setUp() {
        when(credentials.findByLogin("alice")).thenReturn(Optional.of(new CredentialRepository.Credential("1", HASH)));
        when(credentials.replaceHash(eq("1"), eq(HASH), anyString())).thenReturn(true);
        when(credentials.isCurrent(eq("1"), anyString())).thenReturn(true);
        UserDetails account = account("1", "alice", UserDetails.Role.USER);
        when(accounts.getUserDetailsById("1")).thenReturn(account);
    }

    @Test
    void realLoginPersistsAuthenticatedTokenAndAllowsSubsequentRequest() throws Exception {
        MockHttpSession session = login();
        SecurityContext context = (SecurityContext) session.getAttribute(SPRING_SECURITY_CONTEXT_KEY);
        assertInstanceOf(KnowledgerootUserToken.class, context.getAuthentication());
        assertTrue(context.getAuthentication().isAuthenticated());
        assertNull(context.getAuthentication().getCredentials());

        mvc.perform(get("/profile/session-check").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.login").value("alice"));
        mvc.perform(get("/admin/session-check").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordDoesNotCreateAuthenticatedSession() throws Exception {
        MvcResult result = mvc.perform(post("/logmein").with(csrf())
                        .param("username", "alice").param("password", "wrong"))
                .andExpect(redirectedUrl("/login?error")).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertTrue(session == null || session.getAttribute(SPRING_SECURITY_CONTEXT_KEY) == null);
        mvc.perform(get("/profile/session-check")).andExpect(status().is3xxRedirection());
        verify(accounts, never()).getUserDetailsById(anyString());
    }

    @Test
    void unknownOrInactiveAccountIsRejected() throws Exception {
        when(credentials.findByLogin("alice")).thenReturn(Optional.empty());
        mvc.perform(post("/logmein").with(csrf()).param("username", "alice").param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?error"));
        verify(accounts, never()).getUserDetailsById(anyString());
    }

    @Test
    void accountDisabledBetweenPasswordCheckAndContextLookupIsRejected() throws Exception {
        when(accounts.getUserDetailsById("1")).thenThrow(new UserNotFoundException("inactive"));
        mvc.perform(post("/logmein").with(csrf()).param("username", "alice").param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?error"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-hash", "$2$invalid$salt$hash", "$99$1000$salt$hash"})
    void malformedPasswordHashesFailLoginWithoutServerError(String hash) throws Exception {
        when(credentials.findByLogin("alice")).thenReturn(Optional.of(new CredentialRepository.Credential("1", hash)));
        mvc.perform(post("/logmein").with(csrf()).param("username", "alice").param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mvc.perform(post("/logmein").param("username", "alice").param("password", PASSWORD))
                .andExpect(status().isForbidden());
        verifyNoInteractions(credentials, accounts, limiter);
    }

    @Test
    void loginRotatesAnExistingSessionId() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String previousId = session.getId();
        mvc.perform(post("/logmein").session(session).with(csrf())
                        .param("username", "alice").param("password", PASSWORD))
                .andExpect(redirectedUrl("/login/success"));
        assertNotEquals(previousId, session.getId());
    }

    @Test
    void logoutInvalidatesSessionAndRemovesAuthentication() throws Exception {
        MockHttpSession session = login();
        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/logout/success"));
        assertTrue(session.isInvalid());
        mvc.perform(get("/profile/session-check")).andExpect(status().is3xxRedirection());
    }

    @Test
    void logoutWithoutCsrfCannotEndTheSession() throws Exception {
        MockHttpSession session = login();
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        assertFalse(session.isInvalid());
        mvc.perform(get("/profile/session-check").session(session)).andExpect(status().isOk());
    }

    @Test
    void disablingOrDeletingAccountInvalidatesEachExistingSessionOnItsNextRequest() throws Exception {
        MockHttpSession first = login();
        MockHttpSession second = login();
        when(accounts.getUserDetailsById("1")).thenThrow(new UserNotFoundException("inactive or deleted"));

        mvc.perform(get("/profile/session-check").session(first)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/ui/page/session-check").session(second))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("GUEST"));
        assertTrue(first.isInvalid());
        assertTrue(second.isInvalid());
    }

    @ParameterizedTest
    @EnumSource(value = UserDetails.Role.class, names = {"ADMIN", "USER"})
    void roleChangeRequiresNewLoginBeforeAuthorizingTheRequest(UserDetails.Role previousRole) throws Exception {
        UserDetails original = account("1", "alice", previousRole);
        when(accounts.getUserDetailsById("1")).thenReturn(original);
        MockHttpSession session = login();
        mvc.perform(get("/admin/session-check").session(session))
                .andExpect(status().is(previousRole == UserDetails.Role.ADMIN ? 200 : 403));

        UserDetails.Role newRole = previousRole == UserDetails.Role.ADMIN ? UserDetails.Role.USER : UserDetails.Role.ADMIN;
        when(accounts.getUserDetailsById("1")).thenReturn(account("1", "alice", newRole));
        mvc.perform(get("/admin/session-check").session(session)).andExpect(status().is3xxRedirection());
        assertTrue(session.isInvalid());
    }

    @Test
    void renamedAccountIsLookedUpByIdAndCannotSwitchToReusedLogin() throws Exception {
        MockHttpSession session = login();
        when(accounts.getUserDetailsById("1")).thenReturn(account("1", "renamed", UserDetails.Role.USER));
        when(accounts.getUserDetails("alice")).thenReturn(account("2", "alice", UserDetails.Role.ADMIN));
        clearInvocations(accounts);

        mvc.perform(get("/profile/session-check").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.login").value("renamed"))
                .andExpect(jsonPath("$.role").value("USER"));
        verify(accounts).getUserDetailsById("1");
        verify(accounts, never()).getUserDetails(anyString());
    }

    @Test
    void serializedSecurityContextCanBeRestoredAndRevalidated() throws Exception {
        MockHttpSession original = login();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original.getAttribute(SPRING_SECURITY_CONTEXT_KEY));
        }
        MockHttpSession restored = new MockHttpSession();
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored.setAttribute(SPRING_SECURITY_CONTEXT_KEY, input.readObject());
        }
        mvc.perform(get("/profile/session-check").session(restored))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    void unauthenticatedLegacyTokenCannotProvideAUserContext() throws Exception {
        KnowledgerootUserToken token = new KnowledgerootUserToken(account("1", "alice", UserDetails.Role.USER));
        token.setAuthenticated(false);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(token));
        mvc.perform(get("/ui/page/session-check").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("GUEST"));
        assertTrue(session.isInvalid());
    }

    @Test
    void databaseFailureDoesNotAllowAccessUsingStaleSessionPermissions() throws Exception {
        MockHttpSession session = login();
        when(accounts.getUserDetailsById("1")).thenThrow(new DataAccessResourceFailureException("unavailable"));
        assertThrows(DataAccessResourceFailureException.class,
                () -> mvc.perform(get("/profile/session-check").session(session)));
    }

    @Test
    void loginNormalizationDoesNotDependOnServerLocale() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            mvc.perform(post("/logmein").with(csrf()).param("username", "ALICE").param("password", PASSWORD))
                    .andExpect(redirectedUrl("/login/success"));
            verify(credentials).findByLogin("alice");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void passwordChangeInvalidatesExistingSessions() throws Exception {
        MockHttpSession session = login();
        when(accounts.getUserDetailsById("1")).thenReturn(UserDetails.builder().userId("1").login("alice")
                .role(UserDetails.Role.USER).credentialTag("changed-password").build());
        mvc.perform(get("/profile/session-check").session(session)).andExpect(status().is3xxRedirection());
        assertTrue(session.isInvalid());
    }

    @Test
    void throttledLoginDoesNotCheckCredentialsOrCreateSession() throws Exception {
        doThrow(new org.springframework.security.authentication.BadCredentialsException("Invalid username or password"))
                .when(limiter).requireAttempt(eq("alice"), anyString());
        mvc.perform(post("/logmein").with(csrf()).param("username", "alice").param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?error"));
        verifyNoInteractions(credentials, accounts);
    }

    @Test
    void forwardedHeaderDoesNotOverrideTheLimiterSourceAddress() throws Exception {
        mvc.perform(post("/logmein").with(csrf())
                        .with(request -> { request.setRemoteAddr("192.0.2.10"); return request; })
                        .header("X-Forwarded-For", "192.0.2.99").param("username", "ALICE").param("password", PASSWORD))
                .andExpect(redirectedUrl("/login/success"));
        verify(limiter).requireAttempt("alice", "192.0.2.10");
    }

    @Test
    void preUpgradeSessionWithoutCredentialTagRequiresNewLogin() throws Exception {
        var previousUser = UserDetails.builder().userId("1").login("alice").role(UserDetails.Role.USER).build();
        var session = new MockHttpSession();
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(new KnowledgerootUserToken(previousUser)));
        mvc.perform(get("/profile/session-check").session(session)).andExpect(status().is3xxRedirection());
        assertTrue(session.isInvalid());
    }

    @Test
    void unavailableLoginDatabaseFailsWithoutAuthenticatedSession() throws Exception {
        when(credentials.findByLogin("alice")).thenThrow(new DataAccessResourceFailureException("internal database detail"));
        MvcResult result = mvc.perform(post("/logmein").with(csrf())
                        .param("username", "alice").param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?error")).andReturn();
        var session = result.getRequest().getSession(false);
        assertTrue(session == null || session.getAttribute(SPRING_SECURITY_CONTEXT_KEY) == null);
        verifyNoInteractions(accounts);
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mvc.perform(post("/logmein").with(csrf())
                        .param("username", "alice").param("password", PASSWORD))
                .andExpect(redirectedUrl("/login/success")).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private UserDetails account(String id, String login, UserDetails.Role role) {
        return UserDetails.builder().userId(id).login(login).role(role).credentialTag("initial-password").build();
    }
}
