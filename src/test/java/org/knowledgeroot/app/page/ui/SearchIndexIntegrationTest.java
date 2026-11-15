package org.knowledgeroot.app.page.ui;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.file.domain.AttachmentIndex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The ranked full-text search (R68): titles, labels, content and attachment texts, the substring fallback, rights. */
class SearchIndexIntegrationTest extends IsolatedApplicationTest {
    @Autowired AttachmentIndex index;

    private int page(Client client, String name, String content, List<String> labels) throws Exception {
        String labelJson = labels.isEmpty() ? "[]" : "[\"" + String.join("\",\"", labels) + "\"]";
        String created = client.perform(post("/api/v1/pages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parent\":" + pageId + ",\"name\":\"" + name + "\",\"content\":\"" + content + "\",\"labels\":" + labelJson + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    private List<Integer> search(Client client, String term) throws Exception {
        String json = client.perform(get("/api/v1/search").param("q", term)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.items[*].id");
    }

    @Test void hitsAreRankedByTitleLabelsContentAndAttachmentsAndShortTermsFallBackToSubstrings() throws Exception {
        String term = "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var author = login(editor);
        int byTitle = page(author, "Runbook " + term, "<p>Operations notes</p>", List.of());
        int byContent = page(author, "Cluster notes " + pageId, "<p>The " + term + " upgrade steps in detail</p>", List.of());
        int byLabel = page(author, "Platform " + pageId, "<p>Nothing to see</p>", List.of(term));
        int byAttachment = page(author, "Archive " + pageId, "<p>Nothing here either</p>", List.of());
        author.perform(multipart("/api/v1/pages/" + byAttachment + "/files")
                        .file(new MockMultipartFile("file", "cheatsheet.txt", "text/plain", ("kubectl commands for " + term + " clusters").getBytes())))
                .andExpect(status().isCreated());
        int attachmentId = jdbc.queryForObject("SELECT id FROM file WHERE page_id = ? AND name = 'cheatsheet.txt'", Integer.class, byAttachment);
        assertTrue(jdbc.queryForObject("SELECT text FROM file_text WHERE file_id = ?", String.class, attachmentId).contains("kubectl"), "the text is indexed on upload");

        // Ranking tiers: title, label, content, attachment.
        var reader = login(this.reader);
        List<Integer> hits = search(reader, term);
        assertEquals(List.of(byTitle, byLabel, byContent, byAttachment), hits, "title, then label, then content, then attachment");
        // Words match as prefixes and in any order; the attachment hit names the file and quotes its text.
        assertEquals(List.of(byTitle), search(reader, "runbook " + term.substring(0, 8)), "words match as prefixes, in any order, all required");
        String api = reader.perform(get("/api/v1/search").param("q", term)).andReturn().getResponse().getContentAsString();
        List<String> attachments = JsonPath.read(api, "$.items[?(@.id == " + byAttachment + ")].attachment");
        assertEquals(List.of("cheatsheet.txt"), attachments, api);
        List<String> snippets = JsonPath.read(api, "$.items[?(@.id == " + byAttachment + ")].snippet");
        assertTrue(snippets.getFirst().contains("kubectl commands"), snippets.toString());
        assertNull(JsonPath.<List<Object>>read(api, "$.items[?(@.id == " + byTitle + ")].attachment").getFirst());
        String html = reader.perform(get("/search").param("q", term)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Attachment: cheatsheet.txt") && html.contains("kubectl commands"), html);

        // Rights are unchanged: the outsider sees nothing; a guest sees nothing of this subtree either.
        assertEquals(List.of(), search(login(outsider), term));
        assertFalse(mvc.perform(get("/search").param("q", term)).andReturn().getResponse().getContentAsString().contains("Runbook " + term));

        // Short words and parts of words have no index entry; the substring search of old answers them.
        assertTrue(search(reader, "zq").contains(byTitle), "two letters fall back to the substring search");
        String inner = term.substring(4, 10);
        assertTrue(search(reader, inner).containsAll(List.of(byTitle, byContent)), "a part of a word is still found");
        assertEquals(List.of(), search(reader, term + "missing"));

        // The index follows edits: a renamed page and a removed attachment drop out, a new label comes in.
        long revision = jdbc.queryForObject("SELECT revision FROM page WHERE id = ?", Long.class, byTitle);
        author.perform(put("/api/v1/pages/" + byTitle).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Runbook renamed " + pageId + "\",\"content\":\"<p>Operations notes</p>\",\"revision\":" + revision + "}"))
                .andExpect(status().isOk());
        author.perform(delete("/api/v1/files/" + attachmentId)).andExpect(status().isNoContent());
        hits = search(reader, term);
        assertFalse(hits.contains(byTitle) || hits.contains(byAttachment), hits.toString());
        assertEquals(List.of(byLabel, byContent), hits, "labels outrank content");
    }

    @Test void theCatchUpIndexesAttachmentsWithoutTextOnceAndLeavesBinariesAlone() throws Exception {
        var author = login(editor);
        author.perform(multipart("/api/v1/pages/" + pageId + "/files")
                        .file(new MockMultipartFile("file", "minutes.md", "text/markdown", "# Minutes\n\nDecided the catchup fixture".getBytes()))
                        .file(new MockMultipartFile("file", "logo.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'})))
                .andExpect(status().isCreated());
        int minutes = jdbc.queryForObject("SELECT id FROM file WHERE page_id = ? AND name = 'minutes.md'", Integer.class, pageId);
        int logo = jdbc.queryForObject("SELECT id FROM file WHERE page_id = ? AND name = 'logo.png'", Integer.class, pageId);
        assertEquals("", jdbc.queryForObject("SELECT text FROM file_text WHERE file_id = ?", String.class, logo), "binaries get an empty row");
        jdbc.update("DELETE FROM file_text WHERE file_id IN (?, ?)", minutes, logo);
        assertEquals(2, index.catchUp());
        assertTrue(jdbc.queryForObject("SELECT text FROM file_text WHERE file_id = ?", String.class, minutes).contains("catchup fixture"));
        assertEquals(0, index.catchUp(), "nothing is left to index");
        assertTrue(search(login(reader), "catchup fixture").contains(pageId));
        assertEquals("minutes.md", JsonPath.<List<String>>read(login(reader).perform(get("/api/v1/search").param("q", "catchup"))
                .andReturn().getResponse().getContentAsString(), "$.items[?(@.id == " + pageId + ")].attachment").getFirst());
    }
}
