package org.knowledgeroot.app.page.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.web.util.HtmlUtils;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Interface languages (R57): browser language, profile choice, administrator choice and translated reasons. */
class I18nWebIntegrationTest extends IsolatedApplicationTest {
    private static final Locale DUTCH = Locale.forLanguageTag("nl");
    @Autowired MessageSource messages;

    private String text(String key, Locale locale, Object... args) {
        return HtmlUtils.htmlEscape(messages.getMessage(key, args, locale), "UTF-8");
    }

    private String html(Client client, String path, String acceptLanguage) throws Exception {
        var request = get(path);
        if (acceptLanguage != null) request.header("Accept-Language", acceptLanguage);
        return client.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test void guestsSeeTheirBrowserLanguageAndEnglishOtherwise() throws Exception {
        String german = mvc.perform(get("/login?logout=true").header("Accept-Language", "de-DE,de;q=0.9,en;q=0.5"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(german.contains("lang=\"de\""), "html lang follows the language");
        assertTrue(german.contains(text("login.logged-out", Locale.GERMAN)));

        String fallback = mvc.perform(get("/login?logout=true").header("Accept-Language", "ja"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(fallback.contains("lang=\"en\""));
        assertTrue(fallback.contains(text("login.logged-out", Locale.ENGLISH)));
    }

    @Test void theProfileLanguageWinsOverTheBrowserAndChangesAtOnce() throws Exception {
        var client = login(editor);
        assertTrue(html(client, "/profile", "de").contains(text("profile.show.subtitle", Locale.GERMAN)),
                "automatic follows the browser");

        client.perform(post("/profile").param("login", editor).param("email", "").param("firstName", "")
                        .param("surName", "").param("language", "fr").header("HX-Request", "true"))
                .andExpect(status().isOk()).andExpect(header().string("HX-Redirect", "/profile?updated=true"));
        assertEquals("fr", jdbc.queryForObject("SELECT language FROM user WHERE id=?", String.class, editorId));

        String french = html(client, "/profile?updated=true", "de");
        assertTrue(french.contains("lang=\"fr\""));
        assertTrue(french.contains(text("profile.show.subtitle", Locale.FRENCH)));
        assertTrue(french.contains(text("profile.show.updated", Locale.FRENCH)));
        assertTrue(french.contains("window.krI18n"), "the page head carries the script messages");
        assertTrue(french.contains("\"request.failed\""), "the script messages are keyed without the js. prefix");

        // Back to automatic: the browser decides again; an unchanged language needs no full reload.
        client.perform(post("/profile").param("login", editor).param("email", "").param("firstName", "")
                .param("surName", "").param("language", "").header("HX-Request", "true"))
                .andExpect(header().string("HX-Redirect", "/profile?updated=true"));
        client.perform(post("/profile").param("login", editor).param("email", "").param("firstName", "")
                        .param("surName", "").param("language", "").header("HX-Request", "true"))
                .andExpect(header().doesNotExist("HX-Redirect"));
        assertTrue(html(client, "/profile", "it").contains("lang=\"it\""));
    }

    @Test void unsupportedLanguagesAreRejected() throws Exception {
        var client = login(editor);
        client.perform(post("/profile").param("login", editor).param("email", "").param("firstName", "")
                .param("surName", "").param("language", "en_US")).andExpect(status().isBadRequest());
        assertEquals("", jdbc.queryForObject("SELECT language FROM user WHERE id=?", String.class, editorId));
    }

    @Test void administratorsChooseTheLanguageOfAUser() throws Exception {
        var admin = login("integration.admin");
        String form = html(admin, "/admin/users/" + editorId, "en");
        assertTrue(form.contains("name=\"language\""), "the user form offers the language");

        admin.perform(post("/admin/users/" + editorId).param("login", editor).param("email", "")
                        .param("firstName", "").param("lastName", "").param("password", "").param("language", "nl"))
                .andExpect(status().is3xxRedirection());
        assertEquals("nl", jdbc.queryForObject("SELECT language FROM user WHERE id=?", String.class, editorId));

        var user = login(editor);
        String dutch = html(user, "/profile", "en");
        assertTrue(dutch.contains("lang=\"nl\""));
        assertTrue(dutch.contains(text("profile.show.subtitle", DUTCH)));
        assertTrue(dutch.contains("value=\"nl\" selected") || dutch.contains("selected=\"selected\" value=\"nl\"")
                || dutch.matches("(?s).*<option[^>]*value=\"nl\"[^>]*selected.*"), "the profile shows the chosen language");
    }

    @Test void reasonsFromTheDomainAppearTranslated() throws Exception {
        jdbc.update("UPDATE user SET language='de' WHERE id=?", adminId);
        try {
            var admin = login("integration.admin");
            String page = admin.perform(post("/spaces").param("key", "KR").param("name", "Duplicate"))
                    .andReturn().getResponse().getContentAsString();
            assertTrue(page.contains(text("reason.a-space-with-this-key-already-exists", Locale.GERMAN)), page);
        } finally {
            jdbc.update("UPDATE user SET language='' WHERE id=?", adminId);
        }
    }

    @Test void noAccountKeepsTheOldPlaceholderLanguage() {
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM user WHERE language NOT IN ('', 'en', 'de', 'fr', 'es', 'it', 'nl')", Integer.class));
    }
}
