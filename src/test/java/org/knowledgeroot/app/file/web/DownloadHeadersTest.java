package org.knowledgeroot.app.file.web;

import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.file.domain.File;
import org.springframework.http.ContentDisposition;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class DownloadHeadersTest {
    @Test
    void unicodeAndQuotesRoundTripInAsciiOnlyAttachmentHeader() {
        var response = new MockHttpServletResponse();
        String name = "Büro \"Bericht\" 📎.txt";
        DownloadHeaders.apply(response, File.builder().name("C:\\fakepath\\" + name)
                .type("text/plain").size(17).build());
        String disposition = response.getHeader("Content-Disposition");
        assertTrue(disposition.chars().allMatch(c -> c >= 32 && c < 127));
        assertEquals(name, ContentDisposition.parse(disposition).getFilename());
        assertTrue(disposition.startsWith("attachment;"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("private, no-store", response.getHeader("Cache-Control"));
        assertEquals("17", response.getHeader("Content-Length"));
    }

    @Test
    void unsafeLegacyMetadataFallsBackWithoutHeaderInjection() {
        var response = new MockHttpServletResponse();
        DownloadHeaders.apply(response, File.builder().name("file\r\nInjected: yes")
                .type("text/html\r\nInjected: yes").size(-1).build());
        assertEquals("download", ContentDisposition.parse(response.getHeader("Content-Disposition")).getFilename());
        assertEquals("application/octet-stream", response.getContentType());
        assertNull(response.getHeader("Injected"));
        assertNull(response.getHeader("Content-Length"));
    }
}
