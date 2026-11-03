package org.knowledgeroot.app.i18n;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The interface languages Knowledgeroot ships translations for. English is the base bundle and the
 * fallback. A user's {@code language} column holds one of the codes, or the empty string for
 * "follow the browser".
 */
public final class Languages {
    /** A selectable language with its name written in that language. */
    public record Language(String code, String nativeName) {
        public Locale locale() {
            return Locale.forLanguageTag(code);
        }
    }

    public static final Language DEFAULT = new Language("en", "English");

    public static final List<Language> ALL = List.of(
            DEFAULT,
            new Language("de", "Deutsch"),
            new Language("fr", "Français"),
            new Language("es", "Español"),
            new Language("it", "Italiano"),
            new Language("nl", "Nederlands"));

    public static final List<Locale> LOCALES = ALL.stream().map(Language::locale).toList();

    /** Stored value for "use the browser language". */
    public static final String AUTOMATIC = "";

    private Languages() {
    }

    /** The supported language for a stored code; blank, unknown and legacy values such as {@code en_US} yield nothing. */
    public static Optional<Language> find(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        String trimmed = code.trim();
        return ALL.stream().filter(language -> language.code().equals(trimmed)).findFirst();
    }

    /**
     * Normalises a submitted choice: a supported code or {@link #AUTOMATIC}. Anything else is rejected
     * so that the column only ever holds values the resolver understands.
     */
    public static String normalise(String code) {
        if (code == null || code.isBlank()) return AUTOMATIC;
        return find(code).map(Language::code)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported language"));
    }

    /** The best supported locale for an {@code Accept-Language} header, if any range matches. */
    public static Optional<Locale> fromAcceptLanguage(String header) {
        if (header == null || header.isBlank()) return Optional.empty();
        try {
            return Optional.ofNullable(Locale.lookup(Locale.LanguageRange.parse(header), LOCALES));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
