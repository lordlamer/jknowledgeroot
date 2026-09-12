package org.knowledgeroot.app.security.user.api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.knowledgeroot.app.security.auth.DatabaseAuthentificationProvider;
import org.knowledgeroot.app.security.auth.PasswordService;
import org.knowledgeroot.app.security.config.WebSecurityConfig;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.user.domain.GroupDao;
import org.knowledgeroot.app.security.user.domain.UserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({UserRestController.class, GroupRestController.class})
@Import(WebSecurityConfig.class)
class AdminFilterWebTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserDao users;
    @MockitoBean GroupDao groups;
    @MockitoBean UserContext context;
    @MockitoBean DatabaseAuthentificationProvider provider;
    @MockitoBean PasswordService passwords;

    @BeforeEach void caller() {
        when(context.getUserContext()).thenReturn(UserDetails.builder().userId("1").role(UserDetails.Role.ADMIN).build());
    }

    @ParameterizedTest
    @CsvSource({"/user,limit,101", "/group,limit,101", "/user,start,-1", "/group,start,-1",
            "/user,time_start.begin,2026-02-30T00:00:00", "/group,time_start.begin,2026-02-30T00:00:00"})
    void invalidFiltersNeverReachDaos(String path, String parameter, String value) throws Exception {
        mvc.perform(get(path).param(parameter, value).with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
        verifyNoInteractions(users, groups);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/user/999", "/group/999"})
    void missingAdminRecordsReturnNotFound(String path) throws Exception {
        mvc.perform(get(path).with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound());
    }
}
