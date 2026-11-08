package org.knowledgeroot.app.page.ui;

import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.page.collab.CollaborationHandler;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Live collaboration (R63) over a real port: who may open the socket, replay and relay of updates
 * and awareness, compaction, publishing from the live document (the room stays), and the ways a
 * room ends (content from outside the live document, lost permission).
 */
class LiveCollaborationIntegrationTest extends McpTestSupport {
    @Autowired CollaborationHandler handler;

    /** Frames and close events of one socket, in arrival order. */
    static class Peer implements WebSocket.Listener {
        final BlockingQueue<byte[]> frames = new LinkedBlockingQueue<>();
        final BlockingQueue<Integer> closes = new LinkedBlockingQueue<>();
        private ByteBuffer pending = ByteBuffer.allocate(0);
        WebSocket socket;

        @Override public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            var joined = ByteBuffer.allocate(pending.remaining() + data.remaining()).put(pending).put(data).flip();
            if (last) { frames.add(bytes(joined)); pending = ByteBuffer.allocate(0); }
            else pending = joined;
            webSocket.request(1);
            return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closes.add(statusCode);
            return null;
        }
        @Override public void onError(WebSocket webSocket, Throwable error) { closes.add(-1); }

        byte[] frame() throws InterruptedException {
            byte[] frame = frames.poll(10, TimeUnit.SECONDS);
            assertNotNull(frame, "expected a frame");
            return frame;
        }
        void send(int type, byte[] payload) {
            var frame = ByteBuffer.allocate(1 + payload.length).put((byte) type).put(payload).flip();
            socket.sendBinary(frame, true).join();
        }
        int closed() throws InterruptedException {
            Integer code = closes.poll(10, TimeUnit.SECONDS);
            assertNotNull(code, "expected the socket to close");
            return code;
        }
        static byte[] bytes(ByteBuffer buffer) { byte[] out = new byte[buffer.remaining()]; buffer.get(out); return out; }
        static byte[] tail(byte[] frame, int from) { byte[] out = new byte[frame.length - from]; System.arraycopy(frame, from, out, 0, out.length); return out; }
        static long id(byte[] frame) { return ByteBuffer.wrap(frame, 1, 8).getLong(); }
    }

    /** Signs in through the real login form so the cookie jar holds a session. */
    HttpClient signedIn(String login) throws Exception {
        var client = browser();
        var form = fetch(client, base() + "/login");
        assertEquals(200, form.statusCode());
        var response = postForm(client, base() + "/logmein", form("username", login, "password", PASSWORD, "_csrf", csrf(form.body())));
        assertEquals("/login/success", URI.create(location(response)).getPath());
        return client;
    }

    Peer open(HttpClient client, int page, String origin) throws Exception {
        var peer = new Peer();
        var builder = client.newWebSocketBuilder();
        if (origin != null) builder.header("Origin", origin);
        peer.socket = builder.buildAsync(URI.create("ws://localhost:" + port + "/ui/page/" + page + "/collab"), peer).join();
        return peer;
    }

    int rows(int page) { return jdbc.queryForObject("SELECT COUNT(*) FROM page_collab_update WHERE page_id=?", Integer.class, page); }

    /** Sends complete before the server has handled the frame; wait for the store to catch up. */
    void awaitRows(int page, int expected) throws InterruptedException {
        for (int i = 0; i < 100 && rows(page) != expected; i++) Thread.sleep(50);
        assertEquals(expected, rows(page));
    }

    @Test void onlyEditorsFromTheOwnOriginMayOpenTheSocket() throws Exception {
        var reader = signedIn(this.reader);
        var refused = assertThrows(CompletionException.class, () -> open(reader, pageId, base()));
        assertEquals(403, ((WebSocketHandshakeException) refused.getCause()).getResponse().statusCode());
        var editor = signedIn(this.editor);
        var crossSite = assertThrows(CompletionException.class, () -> open(editor, pageId, "https://evil.example.com"));
        assertEquals(403, ((WebSocketHandshakeException) crossSite.getCause()).getResponse().statusCode());
        var noOrigin = assertThrows(CompletionException.class, () -> open(editor, pageId, null));
        assertEquals(403, ((WebSocketHandshakeException) noOrigin.getCause()).getResponse().statusCode());
        var missing = assertThrows(CompletionException.class, () -> open(editor, 999_999, base()));
        assertEquals(403, ((WebSocketHandshakeException) missing.getCause()).getResponse().statusCode());
        var accepted = open(editor, pageId, base());
        assertEquals(CollaborationHandler.READY, accepted.frame()[0]);
        accepted.socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    @Test void updatesAreStoredReplayedRelayedAndCompactedAndPublishingKeepsTheRoom() throws Exception {
        var client = signedIn(editor);
        var first = open(client, pageId, base());
        byte[] ready = first.frame();
        assertEquals(CollaborationHandler.READY, ready[0]);
        assertEquals(0L, Peer.id(ready));
        assertEquals(1, ready[9], "the first browser on an empty log seeds the document");

        first.send(CollaborationHandler.UPDATE, new byte[]{10, 11, 12});
        first.send(CollaborationHandler.UPDATE, new byte[]{20, 21});
        awaitRows(pageId, 2);

        // A second browser gets the replay in order, then READY without the seed flag.
        var second = open(client, pageId, base());
        byte[] replayed = second.frame();
        assertEquals(CollaborationHandler.UPDATE, replayed[0]);
        assertArrayEquals(new byte[]{10, 11, 12}, Peer.tail(replayed, 9));
        long firstId = Peer.id(replayed);
        byte[] replayedSecond = second.frame();
        assertArrayEquals(new byte[]{20, 21}, Peer.tail(replayedSecond, 9));
        long lastId = Peer.id(replayedSecond);
        assertTrue(lastId > firstId);
        byte[] secondReady = second.frame();
        assertEquals(CollaborationHandler.READY, secondReady[0]);
        assertEquals(lastId, Peer.id(secondReady));
        assertEquals(0, secondReady[9]);
        assertEquals(CollaborationHandler.PEER_JOINED, first.frame()[0], "the first browser re-announces its caret");
        assertEquals(2, handler.openSessions(pageId));

        // Relay: updates reach the other browser with their id, awareness is passed through unchanged.
        second.send(CollaborationHandler.UPDATE, new byte[]{30});
        byte[] relayed = first.frame();
        assertEquals(CollaborationHandler.UPDATE, relayed[0]);
        assertArrayEquals(new byte[]{30}, Peer.tail(relayed, 9));
        assertTrue(Peer.id(relayed) > lastId);
        assertTrue(second.frames.isEmpty(), "the sender gets no echo");
        first.send(CollaborationHandler.AWARENESS, new byte[]{7, 7});
        byte[] awareness = second.frame();
        assertEquals(CollaborationHandler.AWARENESS, awareness[0]);
        assertArrayEquals(new byte[]{7, 7}, Peer.tail(awareness, 1));
        awaitRows(pageId, 3);

        // Compaction: a snapshot replaces the updates up to the given id; later ones stay behind it.
        byte[] snapshot = ByteBuffer.allocate(8 + 2).putLong(lastId).put(new byte[]{99, 98}).array();
        second.send(CollaborationHandler.SNAPSHOT, snapshot);
        awaitRows(pageId, 2);
        var stored = jdbc.queryForList("SELECT update_bytes FROM page_collab_update WHERE page_id=? ORDER BY id", byte[].class, pageId);
        assertArrayEquals(new byte[]{99, 98}, stored.get(0));
        assertArrayEquals(new byte[]{30}, stored.get(1));

        // Publishing from the live editor keeps the room: both browsers learn the new revision and the
        // publisher, the log stays, and editing goes on.
        var session = login(editor);
        session.perform(post("/ui/page/" + pageId + "/edit").param("name", "live").param("content", "<p>live</p>")
                        .param("revision", "0").param("live", "true"))
                .andExpect(status().is3xxRedirection());
        byte[] published = first.frame();
        assertEquals(CollaborationHandler.PUBLISHED, published[0]);
        assertEquals(1L, Peer.id(published));
        assertEquals(editor, new String(Peer.tail(published, 9), StandardCharsets.UTF_8));
        assertEquals(CollaborationHandler.PUBLISHED, second.frame()[0]);
        assertEquals(2, handler.openSessions(pageId));
        assertEquals(2, rows(pageId));
        // A live autosave still carrying the old revision waits for the new one; with it the working copy is saved.
        session.perform(post("/ui/page/" + pageId + "/draft").param("name", "live").param("content", "<p>more</p>")
                .param("revision", "0").param("live", "true")).andExpect(status().isConflict());
        session.perform(post("/ui/page/" + pageId + "/draft").param("name", "live").param("content", "<p>more</p>")
                .param("revision", "1").param("live", "true")).andExpect(status().isOk());
        // Publishing that working copy from the page view keeps the room as well.
        session.perform(post("/ui/page/" + pageId + "/publish").param("revision", "1")).andExpect(status().is3xxRedirection());
        byte[] publishedAgain = first.frame();
        assertEquals(CollaborationHandler.PUBLISHED, publishedAgain[0]);
        assertEquals(2L, Peer.id(publishedAgain));
        assertEquals(CollaborationHandler.PUBLISHED, second.frame()[0]);
        assertEquals(2, rows(pageId));

        // Content from outside the live document (an editor without live session, the API, MCP) drops the log and closes the room.
        session.perform(post("/ui/page/" + pageId + "/edit").param("name", "live").param("content", "<p>plain</p>").param("revision", "2"))
                .andExpect(status().is3xxRedirection());
        assertEquals(4409, first.closed());
        assertEquals(4409, second.closed());
        awaitRows(pageId, 0);
        assertEquals(0, handler.openSessions(pageId));

        // A new editor afterwards starts an empty document again and seeds it.
        var third = open(client, pageId, base());
        byte[] thirdReady = third.frame();
        assertEquals(CollaborationHandler.READY, thirdReady[0]);
        assertEquals(1, thirdReady[9]);
        third.socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    @Test void losingTheEditPermissionDisconnectsAndTheSeederRoleMovesOn() throws Exception {
        var client = signedIn(editor);
        var seeder = open(client, pageId, base());
        assertEquals(1, seeder.frame()[9]);
        var waiting = open(client, pageId, base());
        assertEquals(0, waiting.frame()[9], "only one browser seeds");
        seeder.frame(); // peer joined
        // The seeder leaves without seeding: the waiting browser is asked to seed instead.
        seeder.socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
        byte[] promoted = waiting.frame();
        assertEquals(CollaborationHandler.READY, promoted[0]);
        assertEquals(1, promoted[9]);

        jdbc.update("UPDATE page_permission SET permission_level='view' WHERE page_id=? AND role_id=?", pageId, editorId);
        handler.recheckPermissions();
        assertEquals(4403, waiting.closed());
        assertEquals(0, handler.openSessions(pageId));
        assertEquals(List.of(), jdbc.queryForList("SELECT id FROM page_collab_update WHERE page_id=?", Long.class, pageId));
    }
}
