package org.knowledgeroot.app.sanitizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SanitizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "<noscript><style></noscript><script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "<svg><a xlink:href='javascript:alert(1)'>click</a></svg>",
            "<a href='jav&#x61;script:alert(1)'>click</a>",
            "<iframe srcdoc='<script>alert(1)</script>'></iframe>",
            "<p style='background-image:url(javascript:alert(1))'>text</p>",
            "<math><mtext><img src=x onerror=alert(1)></mtext></math>",
            "<img src='data:text/html,<script>alert(1)</script>'>"
    })
    void rejectsExecutableMarkup(String input) {
        String output = Sanitizer.sanitize(input).toLowerCase(java.util.Locale.ROOT);
        for (String dangerous : new String[]{"<script", "<style", "<noscript", "<svg", "<iframe", "<math", "onerror", "javascript:", "data:text/html"}) {
            assertFalse(output.contains(dangerous), output);
        }
    }

    @Test
    void preservesExistingRichTextTablesImagesAndSafeStyles() {
        String output = Sanitizer.sanitize("<h2>Title</h2><p><strong>Bold</strong> <em>italic</em></p>"
                + "<ul><li>Item</li></ul><table><tbody><tr><td>Cell</td></tr></tbody></table>"
                + "<img src='/ui/file/1/download' alt='Picture'>"
                + "<a href='https://example.org'>Link</a><p style='text-align:center;color:red'>Color</p>");
        for (String preserved : new String[]{"<h2>Title</h2>", "<strong>Bold</strong>", "<em>italic</em>",
                "<li>Item</li>", "<td>Cell</td>", "/ui/file/1/download", "https://example.org", "text-align:center", "color:red"}) {
            assertTrue(output.contains(preserved), output);
        }
    }

    @Test
    void sanitize() {
        String input = "<script>alert('hello')</script>test";
        String expected = "test";
        String actual = Sanitizer.sanitize(input);
        assertEquals(expected, actual, "Sanitizer should remove script tags");
    }
}
