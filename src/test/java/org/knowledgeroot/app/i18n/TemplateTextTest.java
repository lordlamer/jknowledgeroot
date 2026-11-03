package org.knowledgeroot.app.i18n;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against new hard-coded interface text in the templates (R57). Visible text must come from
 * {@code th:text}/{@code th:utext} with a message key; the English text inside the element is only the
 * prototype. Attributes users see (placeholder, title, aria-label, alt, confirmations) need their
 * {@code th:} variant, and expressions must not contain English string literals. Content that must stay
 * as written (licence text, URLs, code examples) carries the standard HTML attribute {@code translate="no"}.
 */
class TemplateTextTest {
    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Pattern LETTERS = Pattern.compile("[A-Za-zÀ-ÿ]{2,}");
    private static final Pattern LITERAL = Pattern.compile("'([^']*)'");
    private static final Set<String> NEUTRAL = Set.of("© Knowledgeroot", "Knowledgeroot", "Knowledgeroot.", "K", "KR", "⌘", "Ctrl", "Esc",
            "ID", "MCP", "OAuth", "HTML", "URL", "PDF", "Markdown", "TOTP", "v0");
    private static final List<String> VISIBLE_ATTRIBUTES = List.of("placeholder", "title", "aria-label", "alt",
            "hx-confirm", "hx-prompt", "label", "data-confirm", "data-title");
    private static final Set<String> STRUCTURAL = Set.of("th:href", "th:src", "th:action", "th:fragment", "th:replace",
            "th:insert", "th:with", "th:if", "th:unless", "th:each", "th:classappend", "th:class", "th:id", "th:switch",
            "th:case", "th:object", "th:field", "th:name", "th:style", "th:styleappend", "th:hx-get", "th:hx-post",
            "th:hx-put", "th:hx-delete", "th:hx-vals", "th:hx-target", "th:hx-trigger");
    private static final Set<String> SKIPPED_ELEMENTS = Set.of("script", "style", "svg");

    @Test void templatesContainNoHardCodedInterfaceText() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".html")).sorted().toList()) {
                check(TEMPLATES.relativize(file).toString().replace('\\', '/'), Files.readString(file), problems);
            }
        }
        assertTrue(problems.isEmpty(), problems.size() + " untranslated texts:\n" + String.join("\n", problems));
    }

    private void check(String name, String source, List<String> problems) {
        String html = source.replaceAll("(?is)<!DOCTYPE[^>]*>", "")
                .replaceAll("\\[\\[[\\s\\S]*?]]|\\[\\([\\s\\S]*?\\)]", " ");
        // The XML parser keeps fragments (rows, list items, th:block) where they are written.
        Document document = Jsoup.parse(html, "", Parser.xmlParser());
        NodeTraversor.traverse(new NodeVisitor() {
            @Override public void head(Node node, int depth) {
                if (node instanceof TextNode text) {
                    String value = text.text().trim();
                    if (value.isEmpty() || !LETTERS.matcher(value).find() || NEUTRAL.contains(value) || dynamic(node)) return;
                    problems.add(name + ": text \"" + abbreviate(value) + "\"");
                } else if (node instanceof Element element) {
                    for (String attribute : VISIBLE_ATTRIBUTES) {
                        String value = element.attr(attribute);
                        if (!element.hasAttr(attribute) || !LETTERS.matcher(value).find() || NEUTRAL.contains(value.trim())) continue;
                        if (element.hasAttr("th:" + attribute) || element.attr("th:attr").contains(attribute)) continue;
                        problems.add(name + ": <" + element.tagName() + " " + attribute + "=\"" + abbreviate(value) + "\">");
                    }
                    for (Attribute attribute : element.attributes()) {
                        String key = attribute.getKey();
                        if (!key.startsWith("th:") || STRUCTURAL.contains(key) || key.startsWith("th:data-")) continue;
                        Matcher literal = LITERAL.matcher(attribute.getValue());
                        while (literal.find()) {
                            String value = literal.group(1);
                            if (!LETTERS.matcher(value).find() || value.matches("^[/#~@?].*") || value.matches("[a-z0-9_.:-]+")
                                    || value.matches("^(yyyy|dd|HH|MM).*")) continue;
                            problems.add(name + ": " + key + " literal '" + abbreviate(value) + "'");
                        }
                    }
                }
            }

            @Override public void tail(Node node, int depth) {
            }
        }, document);
    }

    /** Text replaced at runtime: inside th:text/th:utext, a replaced or removed element, or a script. */
    private static boolean dynamic(Node node) {
        for (Node current = node.parent(); current instanceof Element element; current = current.parent()) {
            String tag = element.tagName().toLowerCase();
            if (SKIPPED_ELEMENTS.contains(tag) || element.attr("translate").equals("no")) return true;
            if (element.hasAttr("th:text") || element.hasAttr("th:utext")) return true;
            if (!tag.equals("html") && (element.hasAttr("th:replace") || element.hasAttr("th:insert"))) return true;
            String remove = element.attr("th:remove");
            if (remove.equals("all") || remove.equals("body")) return true;
        }
        return false;
    }

    private static String abbreviate(String value) {
        String compact = value.replaceAll("\\s+", " ");
        return compact.length() > 80 ? compact.substring(0, 80) + "…" : compact;
    }
}
