package org.knowledgeroot.app.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.knowledgeroot.app.security.auth.DatabaseAuthentificationProvider;
import org.knowledgeroot.app.security.config.WebSecurityConfig;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebErrorsTest.FailingController.class)
@Import({WebSecurityConfig.class, WebErrors.class, WebErrorsTest.FailingController.class})
class WebErrorsTest {
    @Autowired MockMvc mvc;
    @MockitoBean DatabaseAuthentificationProvider provider;
    @MockitoBean UserContext users;

    @org.junit.jupiter.api.BeforeEach
    void caller() {
        org.mockito.Mockito.when(users.getUserContext()).thenReturn(
                org.knowledgeroot.app.security.context.domain.UserDetails.builder()
                        .userId("2").role(org.knowledgeroot.app.security.context.domain.UserDetails.Role.USER).build());
    }

    @ParameterizedTest
    @CsvSource({"missing,404", "invalid,400", "denied,403", "conflict,409", "internal,500"})
    void errorsHaveStableStatusAndNeverExposeExceptionDetails(String kind, int expected) throws Exception {
        mvc.perform(get("/test-errors/" + kind).with(user("reader")))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.status").value(expected))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @RestController
    static class FailingController {
        @GetMapping("/test-errors/{kind}")
        String fail(@PathVariable String kind) {
            throw switch (kind) {
                case "missing" -> new EmptyResultDataAccessException("secret SQL", 1);
                case "invalid" -> new IllegalArgumentException("secret input");
                case "denied" -> new AccessDeniedException("secret permission");
                case "conflict" -> new DataIntegrityViolationException("secret database constraint");
                default -> new IllegalStateException("secret connection credentials");
            };
        }
    }
}
