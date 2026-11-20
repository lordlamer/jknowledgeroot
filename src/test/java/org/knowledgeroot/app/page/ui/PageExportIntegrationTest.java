package org.knowledgeroot.app.page.ui;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Page export (R69): PDF, Word and Markdown downloads with metadata, formatting and embedded attachment images. */
class PageExportIntegrationTest extends IsolatedApplicationTest {

    private static byte[] png(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private int upload(Client client, int page, String name, byte[] bytes) throws Exception {
        client.perform(multipart("/ui/file").file(new MockMultipartFile("file", name, "image/png", bytes)).param("pageId", String.valueOf(page)))
                .andExpect(status().is3xxRedirection());
        return jdbc.queryForObject("SELECT id FROM file WHERE page_id = ? AND name = ?", Integer.class, page, name);
    }

    private void label(int page, String name) {
        jdbc.update("INSERT INTO tag(name) VALUES (?)", name);
        jdbc.update("INSERT INTO tag_content(tag_id,page_id) SELECT id,? FROM tag WHERE name=?", page, name);
    }

    private static String pdfText(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static int pdfImages(byte[] pdf) throws Exception {
        int count = 0;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (PDPage page : document.getPages()) {
                for (COSName name : page.getResources().getXObjectNames()) if (page.getResources().isImageXObject(name)) count++;
            }
        }
        return count;
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws Exception {
        var entries = new HashMap<String, byte[]>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) entries.put(entry.getName(), in.readAllBytes());
        }
        return entries;
    }

    /** With {@code -Dexport.dump=<dir>} the three files are written there for a look at them. */
    private static void dump(byte[] pdf, byte[] docx, String markdown) throws Exception {
        String dir = System.getProperty("export.dump");
        if (dir == null || dir.isBlank()) return;
        var target = java.nio.file.Path.of(dir);
        java.nio.file.Files.createDirectories(target);
        java.nio.file.Files.write(target.resolve("page.pdf"), pdf);
        java.nio.file.Files.write(target.resolve("page.docx"), docx);
        java.nio.file.Files.writeString(target.resolve("page.md"), markdown);
    }

    @Test void readersDownloadPdfWordAndMarkdownWithMetadataFormattingAndImages() throws Exception {
        var editorClient = login(editor);
        int picture = upload(editorClient, pageId, "picture.png", png(900, 300));
        int child = page("child-" + pageId);
        jdbc.update("UPDATE page SET parent = ?, name = ? WHERE id = ?", pageId, "Größenübersicht " + pageId, child);
        grant(child, "user", readerId, "view");
        label(child, "alpha-" + child);
        label(child, "beta-" + child);
        jdbc.update("UPDATE page SET content = ? WHERE id = ?", "<h2>Überschrift</h2>"
                + "<p><strong>Fett</strong> and <em>kursiv</em> with <a href=\"https://example.org/x\">a link</a> and <code>inline code</code>.</p>"
                + "<ul><li>First item</li><li>Second item<ul><li>Nested item</li></ul></li></ul>"
                + "<ol><li>One</li><li>Two</li></ol>"
                + "<table><tbody><tr><th>Head cell</th><td>Body cell</td></tr></tbody></table>"
                + "<pre>line one\nline two</pre>"
                + "<blockquote><p>Quoted text</p></blockquote>"
                + "<p style=\"text-align:center;color:red\">Centered red</p><hr>"
                + "<p><img src=\"/ui/file/" + picture + "/download/picture.png\" alt=\"Embedded picture\"></p>"
                + "<p><img src=\"https://example.org/remote.png\" alt=\"Remote picture\"></p>", child);
        var readerClient = login(reader);

        // PDF: text of title, path, labels, authors and every block; one embedded image, the remote one named in a note.
        var pdf = readerClient.perform(get("/ui/page/" + child + "/export?format=pdf")).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.ISO_8859_1));
        String text = pdfText(pdf);
        for (String expected : new String[]{"Größenübersicht " + pageId, "protected-", "alpha-" + child + ", beta-" + child, "integration.admin",
                "Überschrift", "Fett", "kursiv", "a link", "inline code", "First item", "Nested item", "Two", "Head cell", "Body cell",
                "line two", "Quoted text", "Centered red", "Remote picture", "Path:", "Labels:", "Created by", "Changed by"}) {
            assertTrue(text.contains(expected), "PDF text lacks " + expected + ":\n" + text);
        }
        assertEquals(1, pdfImages(pdf), "only the attachment image is embedded");

        // Word: a zip with the document part containing the texts, a hyperlink, numbering, a table and one media file.
        var docx = readerClient.perform(get("/ui/page/" + child + "/export?format=docx")).andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".docx")))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals("PK", new String(docx, 0, 2, StandardCharsets.ISO_8859_1));
        var parts = unzip(docx);
        String document = new String(parts.get("word/document.xml"), StandardCharsets.UTF_8);
        for (String expected : new String[]{"Größenübersicht " + pageId, "Überschrift", "Fett", "Body cell", "line two", "Quoted text", "Centered red",
                "Remote picture", "Nested item", "<w:hyperlink", "<w:numPr>", "<w:tbl>", "<w:drawing>", "w:val=\"Heading2\"", "w:val=\"center\"", "w:val=\"FF0000\""}) {
            assertTrue(document.contains(expected), "document.xml lacks " + expected);
        }
        assertEquals(1, parts.keySet().stream().filter(name -> name.startsWith("word/media/")).count(), "one embedded picture: " + parts.keySet());
        assertTrue(parts.containsKey("word/numbering.xml") && parts.containsKey("word/styles.xml"), parts.keySet().toString());

        // Markdown: heading, metadata list and the converted content; images keep their address.
        String markdown = readerClient.perform(get("/ui/page/" + child + "/export?format=markdown")).andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".md")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(markdown.startsWith("# Größenübersicht " + pageId + "\n"), markdown);
        for (String expected : new String[]{"- Path: protected-", "- Labels: alpha-" + child + ", beta-" + child, "- Created by integration.admin at",
                "## Überschrift", "**Fett**", "*kursiv*", "[a link](https://example.org/x)", "`inline code`", "- First item", "1. One",
                "Body cell", "line two", "> Quoted text", "![Embedded picture](/ui/file/" + picture + "/download/picture.png)",
                "![Remote picture](https://example.org/remote.png)"}) {
            assertTrue(markdown.contains(expected), "Markdown lacks " + expected + ":\n" + markdown);
        }
        dump(pdf, docx, markdown);
    }

    @Test void accessFollowsTheViewPermissionAndTheFormatIsValidated() throws Exception {
        var outsiderClient = login(outsider);
        outsiderClient.perform(get("/ui/page/" + pageId + "/export?format=pdf")).andExpect(status().isForbidden());
        outsiderClient.perform(get("/api/v1/pages/" + pageId + "/export?format=pdf")).andExpect(status().isForbidden());
        mvc.perform(get("/ui/page/" + pageId + "/export?format=pdf")).andExpect(status().isForbidden());
        var readerClient = login(reader);
        readerClient.perform(get("/ui/page/" + pageId + "/export?format=xlsx")).andExpect(status().isBadRequest());
        readerClient.perform(get("/ui/page/" + pageId + "/export")).andExpect(status().isBadRequest());
        readerClient.perform(get("/ui/page/999999999/export?format=pdf")).andExpect(status().isForbidden());

        // The client API serves the same files; "word" and "md" are accepted spellings.
        var word = readerClient.perform(get("/api/v1/pages/" + pageId + "/export?format=word")).andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(new String(unzip(word).get("word/document.xml"), StandardCharsets.UTF_8).contains("protected-"));
        String markdown = readerClient.perform(get("/api/v1/pages/" + pageId + "/export?format=md")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(markdown.startsWith("# protected-") && markdown.contains("original"), markdown);
        assertTrue(pdfText(readerClient.perform(get("/api/v1/pages/" + pageId + "/export?format=pdf")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).contains("original"));
    }

    @Test void attachmentImagesAreEmbeddedOnlyWhenTheViewerMaySeeTheirPage() throws Exception {
        var adminClient = login("integration.admin");
        int hidden = upload(adminClient, otherPageId, "hidden.png", png(20, 20));
        jdbc.update("UPDATE page SET content = ? WHERE id = ?",
                "<p>Shared text</p><p><img src=\"/ui/file/" + hidden + "/download/hidden.png\" alt=\"Hidden picture\"></p>", pageId);
        // The reader may view the page but not the page the attachment belongs to: the picture becomes a note.
        var readerPdf = login(reader).perform(get("/ui/page/" + pageId + "/export?format=pdf")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals(0, pdfImages(readerPdf));
        assertTrue(pdfText(readerPdf).contains("Hidden picture"));
        var readerDocx = login(reader).perform(get("/ui/page/" + pageId + "/export?format=docx")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals(0, unzip(readerDocx).keySet().stream().filter(name -> name.startsWith("word/media/")).count());
        // The administrator sees both pages and gets the picture.
        var adminPdf = adminClient.perform(get("/ui/page/" + pageId + "/export?format=pdf")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals(1, pdfImages(adminPdf));
    }
}
