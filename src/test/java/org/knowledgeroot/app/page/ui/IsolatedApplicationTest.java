package org.knowledgeroot.app.page.ui;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.testcontainers.containers.MariaDBContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = org.knowledgeroot.KnowledgerootApplication.class, properties = {
        "spring.config.location=classpath:/application.properties", "spring.config.import=", "spring.profiles.active=test",
        "knowledgeroot.demo-data.enabled=false", "knowledgeroot.bootstrap.login=integration.admin",
        "knowledgeroot.bootstrap.password=integration-password-2026", "knowledgeroot.storage.driver=file",
        "knowledgeroot.login.source-attempts=10000", "knowledgeroot.login.account-attempts=10000"})
@AutoConfigureMockMvc
abstract class IsolatedApplicationTest {
    static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>(org.knowledgeroot.test.DatabaseImage.MARIADB);
    static final Path STORAGE;
    static {
        DATABASE.start(); // One disposable database for the shared context; Ryuk owns its lifecycle.
        try { STORAGE = Files.createTempDirectory("knowledgeroot-integration-"); }
        catch (java.io.IOException ex) { throw new ExceptionInInitializerError(ex); }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (var paths = Files.walk(STORAGE)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            } catch (java.io.IOException ignored) { }
        }, "integration-storage-cleanup"));
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
        registry.add("file.storage-dir", STORAGE::toString);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    int pageId, otherPageId, editorId, readerId, outsiderId, adminId;
    String editor, reader, outsider;
    static final String PASSWORD = "integration-password-2026";

    @BeforeEach void fixtures() {
        adminId = jdbc.queryForObject("SELECT id FROM user WHERE login='integration.admin'", Integer.class);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        editor = "editor." + suffix; reader = "reader." + suffix; outsider = "outsider." + suffix;
        editorId = user(editor); readerId = user(reader); outsiderId = user(outsider);
        pageId = page("protected-" + suffix);
        otherPageId = page("other-" + suffix);
        grant(pageId, "user", editorId, "edit");
        grant(pageId, "user", readerId, "view");
    }
    private int user(String login) {
        jdbc.update("INSERT INTO user (login,password,active,created_by,create_date,changed_by,change_date) SELECT ?,password,TRUE,id,NOW(),id,NOW() FROM user WHERE id=?", login, adminId);
        return jdbc.queryForObject("SELECT id FROM user WHERE login=?", Integer.class, login);
    }
    int page(String name) {
        jdbc.update("INSERT INTO page (name,content,active,created_by,create_date,changed_by,change_date) VALUES (?,'original',TRUE,?,NOW(),?,NOW())", name, adminId, adminId);
        return jdbc.queryForObject("SELECT id FROM page WHERE name=?", Integer.class, name);
    }
    int grant(int page, String type, Integer role, String level) {
        jdbc.update("INSERT INTO page_permission (page_id,role_type,role_id,permission_level,created_by,create_date,changed_by,change_date) VALUES (?,?,?,?,?,NOW(),?,NOW())", page,type,role,level,adminId,adminId);
        return jdbc.queryForObject("SELECT MAX(id) FROM page_permission WHERE page_id=?", Integer.class, page);
    }
    Client login(String login) throws Exception {
        var form = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn().getResponse();
        var authenticated = mvc.perform(post("/logmein").cookie(form.getCookies())
                .param("username",login).param("password",PASSWORD).param("_csrf",csrf(form.getContentAsString())))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login/success")).andReturn().getResponse();
        var cookies = new java.util.LinkedHashMap<String,Cookie>();
        for (Cookie cookie : authenticated.getCookies()) cookies.put(cookie.getName(),cookie);
        assertTrue(cookies.containsKey("SESSION"));
        var index = mvc.perform(get("/").cookie(cookies.values().toArray(Cookie[]::new)))
                .andExpect(status().isOk()).andReturn().getResponse();
        for (Cookie cookie : index.getCookies()) cookies.put(cookie.getName(),cookie);
        return new Client(cookies.values().toArray(Cookie[]::new),csrf(index.getContentAsString()));
    }
    private String csrf(String html) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*(?:value|content)=\"([^\"]+)\"").matcher(html);
        assertTrue(matcher.find()); return matcher.group(1);
    }
    class Client {
        final Cookie[] cookies; final String csrf;
        Client(Cookie[] cookies, String csrf) { this.cookies=cookies; this.csrf=csrf; }
        ResultActions perform(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
            return mvc.perform(request.cookie(cookies).header("X-XSRF-TOKEN",csrf));
        }
    }
}
