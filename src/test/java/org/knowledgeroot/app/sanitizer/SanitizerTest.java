package org.knowledgeroot.app.sanitizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SanitizerTest {

    @Test
    void sanitize() {
        String input = "<script>alert('hello')</script>test";
        String expected = "test";
        String actual = Sanitizer.sanitize(input);
        assertEquals(expected, actual, "Sanitizer should remove script tags");
    }
}