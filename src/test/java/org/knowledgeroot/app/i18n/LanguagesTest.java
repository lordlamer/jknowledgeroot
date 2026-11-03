package org.knowledgeroot.app.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.security.auth.KnowledgerootUserToken;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LanguagesTest {
    private final UserLocaleResolver resolver = new UserLocaleResolver();

    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test void storedCodesMapToSupportedLanguagesOnly() {
        assertEquals("de", Languages.find("de").orElseThrow().code());
        assertEquals("nl", Languages.find(" nl ").orElseThrow().code());
        assertTrue(Languages.find("").isEmpty());
        assertTrue(Languages.find(null).isEmpty());
        assertTrue(Languages.find("en_US").isEmpty(), "the old placeholder means automatic");
        assertTrue(Languages.find("xx").isEmpty());
    }

    @Test void submittedChoicesAreNormalisedOrRejected() {
        assertEquals("", Languages.normalise(null));
        assertEquals("", Languages.normalise(" "));
        assertEquals("fr", Languages.normalise("fr"));
        assertThrows(IllegalArgumentException.class, () -> Languages.normalise("de_DE"));
        assertThrows(IllegalArgumentException.class, () -> Languages.normalise("<script>"));
    }

    @Test void acceptLanguagePicksTheBestSupportedRange() {
        assertEquals(Optional.of(Locale.GERMAN), Languages.fromAcceptLanguage("de-CH,de;q=0.9,en;q=0.5"));
        assertEquals(Optional.of(Locale.FRENCH), Languages.fromAcceptLanguage("ja,fr-CA;q=0.8,en;q=0.1"));
        assertEquals(Optional.of(Locale.forLanguageTag("es")), Languages.fromAcceptLanguage("es-419"));
        assertTrue(Languages.fromAcceptLanguage("ja, zh;q=0.5").isEmpty());
        assertTrue(Languages.fromAcceptLanguage("not a header;;q=x").isEmpty());
        assertTrue(Languages.fromAcceptLanguage(null).isEmpty());
    }

    @Test void guestsGetTheBrowserLanguageOrEnglish() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "guest",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertEquals(Locale.ITALIAN, resolver.resolveLocale(request("it-IT,it;q=0.9")));
        assertEquals(Locale.ENGLISH, resolver.resolveLocale(request("ja")));
        assertEquals(Locale.ENGLISH, resolver.resolveLocale(request(null)));
    }

    @Test void theProfileLanguageWinsOverTheBrowser() {
        signIn("nl");
        assertEquals(Locale.forLanguageTag("nl"), resolver.resolveLocale(request("de")));
    }

    @Test void automaticAndLegacyValuesFollowTheBrowser() {
        signIn("");
        assertEquals(Locale.GERMAN, resolver.resolveLocale(request("de")));
        signIn("en_US");
        assertEquals(Locale.forLanguageTag("es"), resolver.resolveLocale(request("es")));
    }

    @Test void theLanguageCannotBeSetThroughTheResolver() {
        assertThrows(UnsupportedOperationException.class,
                () -> resolver.setLocale(new MockHttpServletRequest(), null, Locale.GERMAN));
    }

    private void signIn(String language) {
        var user = UserDetails.builder().userId("7").login("user").role(UserDetails.Role.USER).language(language).build();
        SecurityContextHolder.getContext().setAuthentication(new KnowledgerootUserToken(user));
    }

    private static MockHttpServletRequest request(String acceptLanguage) {
        var request = new MockHttpServletRequest();
        if (acceptLanguage != null) request.addHeader("Accept-Language", acceptLanguage);
        return request;
    }
}
