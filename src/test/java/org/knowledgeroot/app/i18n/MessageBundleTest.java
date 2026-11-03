package org.knowledgeroot.app.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Keeps the message bundles complete and consistent: every language has exactly the English keys with
 * the same parameters, every key the templates and scripts use exists, every literal reason the domain
 * services report can be translated, and the script fallbacks match the English bundle.
 */
class MessageBundleTest {
    private static final Path BUNDLES = Path.of("src/main/resources/i18n");
    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Pattern TEMPLATE_KEY = Pattern.compile("#\\{\\s*([A-Za-z0-9_.\\-]+)|#messages\\.msg(?:OrNull)?\\(\\s*'([^']+)'");
    private static final Pattern SCRIPT_KEY = Pattern.compile("\\b(?:krT|t)\\(\\s*'([a-z0-9.\\-]+)'\\s*,\\s*'((?:[^'\\\\]|\\\\.)*)'");
    private static final Pattern SCRIPT_KEY_ONLY = Pattern.compile("\\bkrT\\(\\s*'([a-z0-9.\\-]+)'\\s*\\)");
    private static final Pattern REASON = Pattern.compile(
            "new ResponseStatusException\\(\\s*HttpStatus\\.[A-Z_]+\\s*,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)");
    private static final Pattern PARAMETER = Pattern.compile("\\{(\\d+)[,}]");

    @Test void everyLanguageHasExactlyTheEnglishKeys() throws IOException {
        Properties base = load("messages.properties");
        for (Languages.Language language : Languages.ALL) {
            if (language == Languages.DEFAULT) continue;
            Properties translated = load("messages_" + language.code() + ".properties");
            Set<String> missing = new TreeSet<>(base.stringPropertyNames());
            missing.removeAll(translated.stringPropertyNames());
            Set<String> extra = new TreeSet<>(translated.stringPropertyNames());
            extra.removeAll(base.stringPropertyNames());
            assertTrue(missing.isEmpty(), language.code() + " lacks " + missing);
            assertTrue(extra.isEmpty(), language.code() + " has unknown keys " + extra);
            for (String key : base.stringPropertyNames()) {
                String value = translated.getProperty(key);
                assertFalse(value.isBlank(), language.code() + ": " + key + " is empty");
                assertEquals(parameters(base.getProperty(key)), parameters(value),
                        language.code() + ": " + key + " must use the same parameters as English");
            }
        }
    }

    @Test void everyMessageIsAValidMessageFormatWithDoubledApostrophes() throws IOException {
        for (String file : bundleFiles()) {
            Properties messages = load(file);
            for (String key : messages.stringPropertyNames()) {
                String value = messages.getProperty(key);
                assertDoesNotThrow(() -> new MessageFormat(value, Locale.ROOT), file + ": " + key);
                String unquoted = value.replace("''", "").replace("'{'", "").replace("'}'", "");
                assertFalse(unquoted.contains("'"), file + ": " + key + " has a single apostrophe, write '' instead: " + value);
            }
        }
    }

    @Test void datePatternsAreValid() throws IOException {
        for (String file : bundleFiles()) {
            Properties messages = load(file);
            for (String key : List.of("format.date", "format.datetime")) {
                String pattern = new MessageFormat(messages.getProperty(key)).format(new Object[0]);
                assertDoesNotThrow(() -> java.time.format.DateTimeFormatter.ofPattern(pattern), file + ": " + key);
            }
        }
    }

    @Test void everyKeyUsedByTheTemplatesExists() throws IOException {
        Properties base = load("messages.properties");
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".html")).toList()) {
                Matcher matcher = TEMPLATE_KEY.matcher(Files.readString(file));
                while (matcher.find()) {
                    String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                    // Keys completed at runtime (#{prefix.__${value}__}) need at least one entry under the prefix.
                    if (key.endsWith("__")) {
                        String prefix = key.substring(0, key.length() - 2);
                        if (base.stringPropertyNames().stream().noneMatch(name -> name.startsWith(prefix)))
                            missing.add(TEMPLATES.relativize(file) + ": " + prefix + "*");
                    } else if (!base.containsKey(key)) missing.add(TEMPLATES.relativize(file) + ": " + key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "Unknown message keys: " + missing);
    }

    @Test void everyScriptKeyExistsWithTheEnglishFallback() throws IOException {
        Properties base = load("messages.properties");
        List<String> problems = new ArrayList<>();
        for (Path file : scriptSources()) {
            String text = Files.readString(file);
            Matcher matcher = SCRIPT_KEY.matcher(text);
            while (matcher.find()) {
                String key = "js." + matcher.group(1);
                String fallback = matcher.group(2).replace("\\'", "'").replace("\\\\", "\\");
                if (!base.containsKey(key)) problems.add(file + ": missing " + key);
                else if (!english(base.getProperty(key)).equals(fallback))
                    problems.add(file + ": fallback of " + key + " is '" + fallback + "' but English is '" + english(base.getProperty(key)) + "'");
            }
            Matcher bare = SCRIPT_KEY_ONLY.matcher(text);
            while (bare.find()) if (!base.containsKey("js." + bare.group(1))) problems.add(file + ": missing js." + bare.group(1));
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test void everyLiteralReasonOfTheDomainCanBeTranslated() throws IOException {
        Properties base = load("messages.properties");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = REASON.matcher(Files.readString(file));
                while (matcher.find()) {
                    String english = matcher.group(1).replace("\\\"", "\"");
                    if (!base.containsKey(UiMessages.key(english))) missing.add(UiMessages.key(english) + "=" + english);
                }
            }
        }
        assertTrue(missing.isEmpty(), "Add these reasons to messages.properties and all languages:\n" + String.join("\n", missing));
    }

    @Test void thereIsABundleForEveryOfferedLanguage() {
        for (Languages.Language language : Languages.ALL) {
            String file = language == Languages.DEFAULT ? "messages.properties" : "messages_" + language.code() + ".properties";
            assertTrue(Files.exists(BUNDLES.resolve(file)), file);
        }
    }

    /** The English text as MessageFormat renders it without arguments ({0} stays, '' becomes '). */
    private static String english(String pattern) {
        return new MessageFormat(pattern, Locale.ROOT).format(new Object[0]);
    }

    private static Set<String> parameters(String value) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PARAMETER.matcher(value);
        while (matcher.find()) found.add(matcher.group(1));
        return found;
    }

    private static List<String> bundleFiles() throws IOException {
        try (Stream<Path> files = Files.list(BUNDLES)) {
            return files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".properties")).sorted().toList();
        }
    }

    private static List<Path> scriptSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            sources.addAll(files.filter(path -> path.toString().endsWith(".html")).toList());
        }
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/static/resources/js"))) {
            sources.addAll(files.filter(path -> path.toString().endsWith(".js")).toList());
        }
        sources.add(Path.of("frontend/editor.js"));
        return sources;
    }

    static Properties load(String file) throws IOException {
        Properties properties = new Properties();
        try (var reader = new InputStreamReader(Files.newInputStream(BUNDLES.resolve(file)), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
