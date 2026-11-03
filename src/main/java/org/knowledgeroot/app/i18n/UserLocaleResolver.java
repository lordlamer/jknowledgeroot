package org.knowledgeroot.app.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.knowledgeroot.app.security.auth.KnowledgerootUserToken;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

/**
 * Picks the interface language: the language chosen in the user's profile (or by an administrator),
 * otherwise the best match of the browser's {@code Accept-Language}, otherwise English. The account is
 * reloaded on every request by the session validation filter, so a changed language applies at once.
 * The bean name {@code localeResolver} makes the DispatcherServlet use it.
 */
@Component("localeResolver")
public class UserLocaleResolver implements LocaleResolver {
    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof KnowledgerootUserToken && authentication.getDetails() instanceof UserDetails user) {
            var chosen = Languages.find(user.getLanguage());
            if (chosen.isPresent()) return chosen.get().locale();
        }
        return Languages.fromAcceptLanguage(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE))
                .orElse(Languages.DEFAULT.locale());
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("The language is stored in the user's profile");
    }
}
