package org.knowledgeroot.app.page.ui;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.notification.MailQueue;
import org.knowledgeroot.app.notification.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Watching, notifications, summaries and the durable mail queue (R66). */
@TestPropertySource(properties = {"knowledgeroot.notifications.enabled=true",
        "knowledgeroot.notifications.public-url=https://knowledge.example", "knowledgeroot.notifications.from=noreply@example.org"})
class NotificationIntegrationTest extends IsolatedApplicationTest {
    @MockitoBean JavaMailSender sender;
    @Autowired MailQueue mails;
    @Autowired NotificationService notifications;
    final BlockingQueue<SimpleMailMessage> messages = new LinkedBlockingQueue<>();
    int kr;

    @BeforeEach void mailbox() {
        jdbc.update("UPDATE user SET email=? WHERE id=?", reader + "@example.org", readerId);
        jdbc.update("UPDATE user SET email=? WHERE id=?", editor + "@example.org", editorId);
        jdbc.update("UPDATE user SET email=? WHERE id=?", outsider + "@example.org", outsiderId);
        kr = jdbc.queryForObject("SELECT id FROM space WHERE space_key = 'KR'", Integer.class);
        doAnswer(call -> { messages.add(new SimpleMailMessage(call.getArgument(0))); return null; })
                .when(sender).send(any(SimpleMailMessage.class));
    }

    private SimpleMailMessage mail() throws Exception {
        var message = messages.poll(10, TimeUnit.SECONDS);
        assertNotNull(message, "a mail was expected");
        return message;
    }

    private void noMail() throws Exception {
        assertNull(messages.poll(2, TimeUnit.SECONDS), "no mail was expected");
    }

    private long revision(int page) {
        return jdbc.queryForObject("SELECT revision FROM page WHERE id = ?", Long.class, page);
    }

    private int countFor(int userId, String kind) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ? AND kind = ?", Integer.class, userId, kind);
    }

    private void publish(Client client, int page, String content) throws Exception {
        String name = jdbc.queryForObject("SELECT name FROM page WHERE id = ?", String.class, page);
        client.perform(put("/api/v1/pages/" + page).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"content\":\"" + content + "\",\"revision\":" + revision(page) + "}"))
                .andExpect(status().isOk());
    }

    @Test void watchersOfPagesAndSpacesAreToldAndMentionsReachTheNamedUser() throws Exception {
        var watcher = login(reader);
        // Watching needs the view right; the button shows the state.
        watcher.perform(post("/ui/page/" + pageId + "/watch")).andExpect(status().isOk()).andExpect(content().string(containsString("Watching")));
        login(outsider).perform(post("/ui/page/" + pageId + "/watch")).andExpect(status().isForbidden());
        mvc.perform(post("/ui/page/" + pageId + "/watch")).andExpect(status().is4xxClientError());
        assertTrue(watcher.perform(get("/ui/page/" + pageId)).andReturn().getResponse().getContentAsString().contains("kr-watch on"));
        // The outsider may read the page from now on and watches the whole space.
        grant(pageId, "user", outsiderId, "view");
        var spaceWatcher = login(outsider);
        spaceWatcher.perform(post("/spaces/" + kr + "/watch")).andExpect(status().isOk()).andExpect(content().string(containsString("Watching space")));
        assertTrue(spaceWatcher.perform(get("/spaces/" + kr)).andReturn().getResponse().getContentAsString().contains("kr-watch on"));

        // A publication reaches the page watcher and the space watcher, never the author.
        var author = login(editor);
        publish(author, pageId, "<p>Version two</p>");
        var first = mail();
        var second = mail();
        var recipients = List.of(first.getTo()[0], second.getTo()[0]);
        assertTrue(recipients.containsAll(List.of(reader + "@example.org", outsider + "@example.org")), recipients.toString());
        assertTrue(first.getSubject().contains("was published") && first.getSubject().contains("protected-"), first.getSubject());
        assertTrue(first.getText().contains("https://knowledge.example/ui/page/" + pageId) && first.getText().contains(editor + " published"), first.getText());
        assertEquals("noreply@example.org", first.getFrom());
        assertEquals(1, countFor(readerId, "published"));
        assertEquals(1, countFor(outsiderId, "published"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ?", Integer.class, editorId));
        for (int i = 0; i < 50 && jdbc.queryForObject("SELECT COUNT(*) FROM mail_queue WHERE sent_date IS NOT NULL AND recipient IN (?, ?)", Integer.class, reader + "@example.org", outsider + "@example.org") < 2; i++) Thread.sleep(100);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mail_queue WHERE sent_date IS NOT NULL AND recipient IN (?, ?)", Integer.class, reader + "@example.org", outsider + "@example.org"));

        // A comment with a mention: the mentioned watcher gets the mention, the other watcher the comment.
        author.perform(post("/ui/page/" + pageId + "/comments").param("content", "Please check @" + reader + ", and @nobody-" + pageId + " too."))
                .andExpect(status().isOk());
        var mails = List.of(mail(), mail());
        var mention = mails.stream().filter(m -> m.getTo()[0].startsWith(reader)).findFirst().orElseThrow();
        var comment = mails.stream().filter(m -> m.getTo()[0].startsWith(outsider)).findFirst().orElseThrow();
        assertTrue(mention.getSubject().contains(editor + " mentioned you"), mention.getSubject());
        assertTrue(mention.getText().contains("Please check @" + reader), mention.getText());
        assertTrue(comment.getSubject().contains("New comment on"), comment.getSubject());
        assertEquals(1, countFor(readerId, "mention"));
        assertEquals(0, countFor(readerId, "comment"));
        assertEquals(1, countFor(outsiderId, "comment"));

        // A new page in the space is news for the space watcher; moving it to the top level too.
        String created = author.perform(post("/api/v1/pages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parent\":" + pageId + ",\"name\":\"child-" + pageId + "\",\"content\":\"<p>child</p>\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int child = JsonPath.read(created, "$.id");
        assertTrue(mail().getTo()[0].startsWith(outsider));
        assertEquals(2, countFor(outsiderId, "published"));
        assertEquals(1, countFor(readerId, "published"), "the page watcher is not told about other pages");
        author.perform(post("/api/v1/pages/" + child + "/move").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parent\":0,\"space\":" + kr + ",\"revision\":" + revision(child) + "}"))
                .andExpect(status().isOk());
        var moved = mail();
        assertTrue(moved.getSubject().contains("was moved") && moved.getText().contains("to the top level of space KR"), moved.getText());
        assertEquals(1, countFor(outsiderId, "moved"));

        // The bell counts unread entries until the history is opened; the API lists the same entries.
        String home = spaceWatcher.perform(get("/")).andReturn().getResponse().getContentAsString();
        assertTrue(home.contains("kr-nav-badge") && home.contains(">4<"), "four unread entries expected in the header");
        String list = spaceWatcher.perform(get("/notifications")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(list.contains(editor + " moved") && list.contains("child-" + pageId) && list.contains("Watched pages and spaces") && list.contains("KR"), list);
        assertFalse(spaceWatcher.perform(get("/")).andReturn().getResponse().getContentAsString().contains("kr-nav-badge"));
        String api = watcher.perform(get("/api/v1/notifications")).andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(2)).andReturn().getResponse().getContentAsString();
        assertEquals(List.of("mention", "published"), JsonPath.read(api, "$.items[*].kind"));
        assertTrue(JsonPath.<String>read(api, "$.items[0].text").contains("mentioned you"), api);
        watcher.perform(post("/api/v1/notifications/read")).andExpect(status().isNoContent());
        watcher.perform(get("/api/v1/notifications")).andExpect(jsonPath("$.unread").value(0));
        assertEquals(true, JsonPath.read(watcher.perform(get("/api/v1/pages/" + pageId)).andReturn().getResponse().getContentAsString(), "$.watched"));

        // Without the view right no notification is written, and stopping the watch ends them.
        jdbc.update("DELETE FROM page_permission WHERE page_id = ? AND role_id = ?", pageId, readerId);
        publish(author, pageId, "<p>Version three</p>");
        assertTrue(mail().getTo()[0].startsWith(outsider));
        noMail();
        assertEquals(1, countFor(readerId, "published"));
        spaceWatcher.perform(delete("/spaces/" + kr + "/watch")).andExpect(status().isOk()).andExpect(content().string(containsString("Watch space")));
        publish(author, pageId, "<p>Version four</p>");
        noMail();
        assertEquals(3, countFor(outsiderId, "published"));
    }

    @Test void summariesFollowThePreferenceAndMentionsCanBeSwitchedOff() throws Exception {
        var watcher = login(reader);
        watcher.perform(post("/ui/page/" + pageId + "/watch")).andExpect(status().isOk());
        watcher.perform(post("/profile/notifications").param("frequency", "daily").param("mentions", "on"))
                .andExpect(redirectedUrl("/profile?notifications"));
        assertTrue(watcher.perform(get("/profile?notifications=1")).andReturn().getResponse().getContentAsString().contains("Notification settings saved."));
        watcher.perform(post("/profile/notifications").param("frequency", "weekly")).andExpect(status().isBadRequest());
        var author = login(editor);
        publish(author, pageId, "<p>Daily one</p>");
        author.perform(post("/ui/page/" + pageId + "/comments").param("content", "Ping @" + reader)).andExpect(status().isOk());
        noMail();
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ? AND mailed = 0", Integer.class, readerId));

        // The summary bundles everything since the last one; a second run within the day sends nothing.
        notifications.sendDigests(LocalDateTime.now());
        var digest = mail();
        assertEquals(reader + "@example.org", digest.getTo()[0]);
        assertTrue(digest.getSubject().contains("2 new notifications"), digest.getSubject());
        assertTrue(digest.getText().contains("- " + editor + " published") && digest.getText().contains("- " + editor + " mentioned you")
                && digest.getText().contains("https://knowledge.example/ui/page/" + pageId), digest.getText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ? AND mailed = 0", Integer.class, readerId));
        publish(author, pageId, "<p>Daily two</p>");
        notifications.sendDigests(LocalDateTime.now());
        noMail();
        notifications.sendDigests(LocalDateTime.now().plusDays(1).plusMinutes(1));
        assertTrue(mail().getSubject().contains("1 new notification"));

        // Never: the history fills, no mail; mentions off: not even a history entry.
        String preference = watcher.perform(put("/api/v1/profile/notifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frequency\":\"never\",\"mentions\":false}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("never", JsonPath.read(preference, "$.frequency"));
        assertEquals(false, JsonPath.read(preference, "$.mentions"));
        assertEquals(true, JsonPath.read(preference, "$.mailsEnabled"));
        int before = countFor(readerId, "mention");
        publish(author, pageId, "<p>Silent</p>");
        author.perform(post("/ui/page/" + pageId + "/comments").param("content", "Again @" + reader)).andExpect(status().isOk());
        noMail();
        assertEquals(before, countFor(readerId, "mention"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ? AND kind = 'comment'", Integer.class, readerId));
        notifications.sendDigests(LocalDateTime.now().plusDays(2));
        noMail();
    }

    @Test void theMailQueueRetriesFailuresAndAdministratorsManageIt() throws Exception {
        doThrow(new org.springframework.mail.MailSendException("relay down")).when(sender).send(any(SimpleMailMessage.class));
        int id = mails.enqueue(readerId, "noreply@example.org", reader + "@example.org", "Queue test " + pageId, "Body");
        for (int i = 0; i < 50 && jdbc.queryForObject("SELECT attempts FROM mail_queue WHERE id = ?", Integer.class, id) == 0; i++) Thread.sleep(100);
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM mail_queue WHERE id = ?", Integer.class, id));
        assertEquals("relay down", jdbc.queryForObject("SELECT last_error FROM mail_queue WHERE id = ?", String.class, id));
        assertTrue(jdbc.queryForObject("SELECT next_attempt > NOW(3) FROM mail_queue WHERE id = ?", Boolean.class, id), "the retry waits");
        // Another round right now leaves the mail alone; it is not due yet.
        mails.deliverDue();
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM mail_queue WHERE id = ?", Integer.class, id));

        login(reader).perform(get("/admin/mail")).andExpect(status().isForbidden());
        var admin = login("integration.admin");
        String page = admin.perform(get("/admin/mail")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("Queue test " + pageId) && page.contains("relay down") && page.contains("data-status=\"pending\""), page);

        // Retry now sends it once the relay is back; the page shows it as sent, delete removes it.
        doAnswer(call -> { messages.add(new SimpleMailMessage(call.getArgument(0))); return null; }).when(sender).send(any(SimpleMailMessage.class));
        admin.perform(post("/admin/mail/" + id + "/retry")).andExpect(redirectedUrl("/admin/mail?retried"));
        assertEquals("Queue test " + pageId, mail().getSubject());
        for (int i = 0; i < 50 && jdbc.queryForObject("SELECT sent_date FROM mail_queue WHERE id = ?", java.sql.Timestamp.class, id) == null; i++) Thread.sleep(100);
        page = admin.perform(get("/admin/mail?retried=1")).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("The mail is queued again.") && page.contains("data-status=\"sent\""), page);
        admin.perform(post("/admin/mail/" + id + "/delete")).andExpect(redirectedUrl("/admin/mail?deleted"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mail_queue WHERE id = ?", Integer.class, id));
        admin.perform(post("/admin/mail/" + id + "/delete")).andExpect(status().isNotFound());

        // After the last attempt the mail waits as failed until an administrator acts.
        doThrow(new org.springframework.mail.MailSendException("still down")).when(sender).send(any(SimpleMailMessage.class));
        int stuck = mails.enqueue(null, "noreply@example.org", "ops@example.org", "Stuck " + pageId, "Body");
        for (int i = 0; i < 50 && jdbc.queryForObject("SELECT attempts FROM mail_queue WHERE id = ?", Integer.class, stuck) == 0; i++) Thread.sleep(100);
        jdbc.update("UPDATE mail_queue SET attempts = ?, next_attempt = NOW(3) WHERE id = ?", MailQueue.MAX_ATTEMPTS, stuck);
        mails.deliverDue();
        assertEquals(MailQueue.MAX_ATTEMPTS, jdbc.queryForObject("SELECT attempts FROM mail_queue WHERE id = ?", Integer.class, stuck), "no more attempts");
        page = admin.perform(get("/admin/mail")).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("data-status=\"failed\"") && page.contains("1 failed"), page);
        jdbc.update("DELETE FROM mail_queue WHERE id = ?", stuck);
    }
}
