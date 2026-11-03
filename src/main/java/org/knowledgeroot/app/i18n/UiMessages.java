package org.knowledgeroot.app.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Translates texts produced in Java for the web interface.
 *
 * <p>Domain services report problems as English {@link ResponseStatusException} reasons. The client
 * API and the mobile app rely on these exact texts, so they stay English there; the HTML controllers
 * pass them through {@link #reason(ResponseStatusException)}, which looks up {@code reason.<slug>} in the
 * message bundles and falls back to the English text for reasons without an entry (for example ones
 * that contain a variable part).
 */
@Component
@RequiredArgsConstructor
public class UiMessages {
    private static final int MAX_SLUG = 80;

    private final MessageSource messages;

    /** The message for {@code code} in the current request's language. */
    public String get(String code, Object... args) {
        return messages.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    /** The message for {@code code} in an explicit language, for work outside a request such as mails. */
    public String get(Locale locale, String code, Object... args) {
        return messages.getMessage(code, args, locale);
    }

    /** The translated reason of an exception, or its English text when there is no translation. */
    public String reason(ResponseStatusException e) {
        return text(e.getReason());
    }

    /** The translated form of an English reason text. */
    public String text(String english) {
        if (english == null || english.isBlank()) return english;
        // No default message here: Spring would run it through MessageFormat and drop its apostrophes.
        String translated = messages.getMessage(key(english), null, null, LocaleContextHolder.getLocale());
        return translated == null ? english : translated;
    }

    /**
     * The bundle key of an English reason: {@code reason.} plus the lower-case words joined by dashes,
     * for example {@code reason.choose-a-role} for "Choose a role.".
     */
    public static String key(String english) {
        String slug = english.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_SLUG) slug = slug.substring(0, MAX_SLUG).replaceAll("-+$", "");
        return "reason." + slug;
    }
}
