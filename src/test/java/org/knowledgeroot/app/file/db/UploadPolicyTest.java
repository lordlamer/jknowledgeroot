package org.knowledgeroot.app.file.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.knowledgeroot.app.file.domain.UploadPolicy;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class UploadPolicyTest {
    private final UploadPolicy policy = new UploadPolicy("4B", "6B", 2);
    private MockMultipartFile file(String name, int bytes) { return new MockMultipartFile("file", name, "text/plain", new byte[bytes]); }

    @Test
    void enforcesPerFileBatchAndCountLimitsIncludingExactBoundary() {
        policy.validate(new MockMultipartFile[]{file("one", 4), file("two", 2)});
        assertEquals(413, assertThrows(ResponseStatusException.class, () -> policy.validate(new MockMultipartFile[]{file("big", 5)})).getStatusCode().value());
        assertEquals(413, assertThrows(ResponseStatusException.class, () -> policy.validate(new MockMultipartFile[]{file("one", 4), file("two", 3)})).getStatusCode().value());
        assertEquals(413, assertThrows(ResponseStatusException.class, () -> policy.validate(new MockMultipartFile[]{file("one", 1), file("two", 1), file("three", 1)})).getStatusCode().value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "..", "\r\nInjected: yes", "nul\u0000name"})
    void rejectsUnsafeNames(String name) { assertThrows(ResponseStatusException.class, () -> policy.validate(new MockMultipartFile[]{file(name, 1)})); }

    @Test
    void removesClientPathsAndRejectsUnsafeMediaTypes() {
        assertEquals("report.txt", UploadPolicy.name("C:\\fakepath\\report.txt"));
        assertEquals("report.txt", UploadPolicy.name("../../report.txt"));
        assertThrows(ResponseStatusException.class, () -> policy.validate(new MockMultipartFile[]{new MockMultipartFile("file", "ok", "text/plain\r\nInjected: yes", new byte[]{1})}));
    }

    @Test
    void stagingStreamsOnceAndCleansUpItsTemporaryFile() throws Exception {
        var file = new MockMultipartFile("file", "ok", "text/plain", new byte[]{1,2,3}) {
            @Override public byte[] getBytes() { throw new AssertionError("must stream"); }
        };
        java.nio.file.Path path;
        try (var staged = StagedUpload.read(file, 3)) {
            path = staged.path;
            assertTrue(Files.exists(path));
            assertEquals(3, staged.size);
            assertEquals("sha256-039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81", staged.key);
        }
        assertFalse(Files.exists(path));
    }

    @Test
    void actualStreamIsBoundedEvenWhenDeclaredSizeIsWrong() {
        var file = new MockMultipartFile("file", "ok", "text/plain", new byte[]{1}) {
            @Override public java.io.InputStream getInputStream() { return new ByteArrayInputStream(new byte[10]); }
        };
        assertEquals(413, assertThrows(ResponseStatusException.class, () -> StagedUpload.read(file, 4)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> StagedUpload.read(file, 20)).getStatusCode().value());
    }
}
