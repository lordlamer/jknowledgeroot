package org.knowledgeroot.app.page.ui;

import org.junit.jupiter.api.Test;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session fixation protection against the real server: Tomcat, Spring Session JDBC and the form login.
 * MockMvc cannot show this since Spring Framework 7.0.10, whose mock request no longer reflects the
 * session id change, so the check runs over HTTP with a real cookie.
 */
class LoginSessionRotationIntegrationTest extends McpTestSupport {
    @Test void aSessionFromBeforeTheLoginGetsANewIdAtLogin() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10)).build();
        // A protected page stores the requested address in a new anonymous session.
        assertEquals(302, fetch(client, base() + "/profile").statusCode());
        var login = fetch(client, base() + "/login");
        String before = session(cookies);
        assertNotNull(before, "an anonymous session exists before the login");

        var response = postForm(client, base() + "/logmein", form("username", "integration.admin",
                "password", PASSWORD, "_csrf", csrf(login.body())));
        assertEquals("/login/success", URI.create(location(response)).getPath());
        String after = session(cookies);
        assertNotNull(after);
        assertNotEquals(before, after, "the login must issue a new session id");

        // The old id no longer authenticates anything; the new one does.
        var stale = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        assertEquals(302, send(stale, java.net.http.HttpRequest.newBuilder(URI.create(base() + "/profile"))
                .header("Cookie", "SESSION=" + before).build()).statusCode());
        assertEquals(200, fetch(client, base() + "/profile").statusCode());
    }

    private static String session(CookieManager cookies) {
        return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("SESSION"))
                .map(HttpCookie::getValue).findFirst().orElse(null);
    }
}
