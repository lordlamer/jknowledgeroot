package org.knowledgeroot.app.i18n;

import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The messages the browser scripts need ({@code js.*} keys), rendered into the page head as
 * {@code window.krI18n} so that the htmx error banner, the upload dialog and the editor toolbar speak
 * the page's language. The key list comes from the English base bundle; each language is resolved once.
 */
@Component
public class JsMessages {
    static final String BUNDLE = "i18n/messages.properties";
    private static final String PREFIX = "js.";

    private final MessageSource messages;
    private final java.util.List<String> keys;
    private final Map<Locale, Map<String, String>> cache = new ConcurrentHashMap<>();

    public JsMessages(MessageSource messages) {
        this.messages = messages;
        try {
            var base = PropertiesLoaderUtils.loadProperties(new ClassPathResource(BUNDLE));
            this.keys = base.stringPropertyNames().stream().filter(key -> key.startsWith(PREFIX)).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Message bundle " + BUNDLE + " is missing", e);
        }
    }

    /** All {@code js.*} messages for a language, keyed without the prefix. */
    public Map<String, String> forLocale(Locale locale) {
        return cache.computeIfAbsent(locale, this::resolve);
    }

    private Map<String, String> resolve(Locale locale) {
        Map<String, String> resolved = new TreeMap<>();
        for (String key : keys) resolved.put(key.substring(PREFIX.length()), messages.getMessage(key, null, key, locale));
        return Map.copyOf(resolved);
    }
}
