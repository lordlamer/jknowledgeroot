package org.knowledgeroot.app.page.domain;

import org.junit.jupiter.api.Test;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class RevisionDiffTest {
    @Test void differencesPreserveBothInputsIncludingWhitespaceAndUnicode() {
        String before = "A café\nold 🌻 text", after = "A café\nnew 🌻 text!";
        var parts = RevisionDiff.compare(before, after);
        assertEquals(before, parts.stream().filter(p -> !p.kind().equals("added")).map(RevisionDiff.Part::text).collect(Collectors.joining()));
        assertEquals(after, parts.stream().filter(p -> !p.kind().equals("removed")).map(RevisionDiff.Part::text).collect(Collectors.joining()));
        assertTrue(parts.stream().anyMatch(p -> p.kind().equals("added")));
        assertTrue(parts.stream().anyMatch(p -> p.kind().equals("removed")));
    }
    @Test void largeVersionsUseBoundedBlocksAndTextProjectionPreservesEntities() {
        String before = "old ".repeat(10_000), after = "new ".repeat(10_000);
        assertEquals(2, RevisionDiff.compare(before, after).size());
        assertEquals("Hello & café\nNext\n", RevisionDiff.text("<p>Hello &amp; <strong>café</strong></p><p>Next</p>"));
        assertEquals("same", RevisionDiff.compare("", "").getFirst().kind());
    }
}
