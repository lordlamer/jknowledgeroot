package org.knowledgeroot.app.page.collab;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.page.domain.PageCollabStore;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.page.domain.WorkingCopyCleared;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Relays the Yjs updates of a page between the browsers that edit it (R63). Frames are binary;
 * the first byte is the type:
 * <ul>
 * <li>0 update: browser → server carries the update; server → browser carries the stored id (8 bytes) and the update</li>
 * <li>1 awareness: carets and names, relayed only</li>
 * <li>2 snapshot: browser → server, 8 bytes "up to id" and a compact state replacing the updates up to that id</li>
 * <li>3 ready: server → browser after the replay, 8 bytes last id and one byte "seed" (1 when the log is empty
 *     and this browser must seed the document from the working copy)</li>
 * <li>4 peer joined: server → browser, asks for the awareness state to be announced again</li>
 * <li>5 snapshot requested: server → browser when the log has grown</li>
 * </ul>
 * One room per page keeps the open sessions; store writes and broadcasts of a page are serialised
 * on the room so that replay, ids and compaction stay in order. Permissions are rechecked every
 * half minute; publishing or discarding the working copy closes the room.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CollaborationHandler extends BinaryWebSocketHandler {
    public static final byte UPDATE = 0, AWARENESS = 1, SNAPSHOT = 2, READY = 3, PEER_JOINED = 4, SNAPSHOT_REQUESTED = 5;
    /** Above this many stored bytes the browser that just wrote is asked for a snapshot. */
    static final long COMPACT_ABOVE_BYTES = 1024 * 1024;
    static final CloseStatus PERMISSION_LOST = new CloseStatus(4403, "You may no longer edit this page");
    static final CloseStatus WORKING_COPY_CLEARED = new CloseStatus(4409, "The working copy was published or discarded");
    private static final int SEND_BUFFER_BYTES = 4 * 1024 * 1024;
    private static final int SEND_TIMEOUT_MS = 10_000;

    private final PageCollabStore store;
    private final PagePermissionDao permissions;
    private final Map<Integer, Room> rooms = new ConcurrentHashMap<>();

    /** The sessions of one page; {@code seeder} is the session that must seed an empty document. */
    private static final class Room {
        final Map<String, WebSocketSession> sessions = new LinkedHashMap<>();
        String seeder;
        boolean seeded;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) throws IOException {
        var session = new ConcurrentWebSocketSessionDecorator(raw, SEND_TIMEOUT_MS, SEND_BUFFER_BYTES);
        PageId page = page(session);
        Room room = rooms.computeIfAbsent(page.value(), id -> new Room());
        synchronized (room) {
            var updates = store.updates(page);
            long lastId = 0;
            for (var update : updates) {
                session.sendMessage(frame(UPDATE, update.id(), update.bytes()));
                lastId = update.id();
            }
            if (!updates.isEmpty()) room.seeded = true;
            boolean seed = !room.seeded && room.seeder == null;
            if (seed) room.seeder = session.getId();
            session.sendMessage(frame(READY, lastId, new byte[]{(byte) (seed ? 1 : 0)}));
            for (WebSocketSession other : room.sessions.values()) send(other, frame(PEER_JOINED, null, null));
            room.sessions.put(session.getId(), session);
        }
        log.debug("Collaboration on page {}: {} joined ({} open)", page.value(), session.getAttributes().get(CollaborationHandshake.LOGIN), room.sessions.size());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession raw, BinaryMessage message) throws IOException {
        PageId page = page(raw);
        Room room = rooms.get(page.value());
        if (room == null) return;
        ByteBuffer payload = message.getPayload();
        if (!payload.hasRemaining()) return;
        byte type = payload.get();
        byte[] bytes = new byte[payload.remaining()];
        payload.get(bytes);
        synchronized (room) {
            WebSocketSession session = room.sessions.get(raw.getId());
            if (session == null) return;
            switch (type) {
                case UPDATE -> {
                    if (bytes.length == 0) return;
                    long id = store.append(page, bytes);
                    room.seeded = true;
                    room.seeder = null;
                    var frame = frame(UPDATE, id, bytes);
                    for (WebSocketSession other : room.sessions.values()) if (other != session) send(other, frame);
                    if (store.size(page) > COMPACT_ABOVE_BYTES) send(session, frame(SNAPSHOT_REQUESTED, null, null));
                }
                case AWARENESS -> {
                    var frame = frame(AWARENESS, null, bytes);
                    for (WebSocketSession other : room.sessions.values()) if (other != session) send(other, frame);
                }
                case SNAPSHOT -> {
                    if (bytes.length <= Long.BYTES) return;
                    long upTo = ByteBuffer.wrap(bytes, 0, Long.BYTES).getLong();
                    byte[] snapshot = new byte[bytes.length - Long.BYTES];
                    System.arraycopy(bytes, Long.BYTES, snapshot, 0, snapshot.length);
                    store.compact(page, snapshot, upTo);
                }
                default -> log.debug("Collaboration on page {}: ignoring frame type {}", page.value(), type);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) throws IOException {
        PageId page = page(raw);
        Room room = rooms.get(page.value());
        if (room == null) return;
        synchronized (room) {
            room.sessions.remove(raw.getId());
            if (raw.getId().equals(room.seeder)) {
                // The seeder left before seeding: the next session takes over so the document is not empty forever.
                room.seeder = null;
                var next = room.sessions.values().stream().findFirst().orElse(null);
                if (next != null && !room.seeded) {
                    room.seeder = next.getId();
                    send(next, frame(READY, 0, new byte[]{1}));
                }
            }
            if (room.sessions.isEmpty()) rooms.remove(page.value(), room);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        log.debug("Collaboration transport error on page {}: {}", session.getAttributes().get(CollaborationHandshake.PAGE), exception.toString());
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    /** Publishing or discarding the working copy restarts the live document; open editors reconnect from the page. */
    @EventListener
    public void onWorkingCopyCleared(WorkingCopyCleared event) {
        Room room = rooms.get(event.pageId().value());
        if (room == null) return;
        List<WebSocketSession> open;
        synchronized (room) {
            open = new ArrayList<>(room.sessions.values());
            room.sessions.clear();
            room.seeded = false;
            room.seeder = null;
        }
        rooms.remove(event.pageId().value(), room);
        for (WebSocketSession session : open) close(session, WORKING_COPY_CLEARED);
    }

    /** Rights change while editors are open: whoever lost the edit permission (or whose page vanished) is disconnected. */
    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT30S")
    public void recheckPermissions() {
        for (var entry : rooms.entrySet()) {
            Room room = entry.getValue();
            List<WebSocketSession> open;
            synchronized (room) { open = new ArrayList<>(room.sessions.values()); }
            for (WebSocketSession session : open) {
                Integer user = (Integer) session.getAttributes().get(CollaborationHandshake.USER);
                if (!permissions.hasUserPermission(new PageId(entry.getKey()), user, PagePermission.PermissionLevel.EDIT)) {
                    close(session, PERMISSION_LOST);
                }
            }
        }
    }

    /** Open sessions per page, for tests and diagnostics. */
    public int openSessions(int pageId) {
        Room room = rooms.get(pageId);
        if (room == null) return 0;
        synchronized (room) { return room.sessions.size(); }
    }

    private static PageId page(WebSocketSession session) {
        return new PageId((Integer) session.getAttributes().get(CollaborationHandshake.PAGE));
    }

    static BinaryMessage frame(byte type, Long id, byte[] bytes) {
        int length = 1 + (id == null ? 0 : Long.BYTES) + (bytes == null ? 0 : bytes.length);
        ByteBuffer buffer = ByteBuffer.allocate(length);
        buffer.put(type);
        if (id != null) buffer.putLong(id);
        if (bytes != null) buffer.put(bytes);
        buffer.flip();
        return new BinaryMessage(buffer);
    }

    private static BinaryMessage frame(byte type, long id, byte[] bytes) {
        return frame(type, Long.valueOf(id), bytes);
    }

    private static void send(WebSocketSession session, BinaryMessage frame) {
        try {
            if (session.isOpen()) session.sendMessage(frame);
        } catch (IOException | IllegalStateException e) {
            log.debug("Collaboration send failed, closing {}: {}", session.getId(), e.toString());
            close(session, CloseStatus.SERVER_ERROR);
        }
    }

    private static void close(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) session.close(status);
        } catch (IOException ignored) {
            // The peer is already gone.
        }
    }
}
