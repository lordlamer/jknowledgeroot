package org.knowledgeroot.app.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.i18n.Languages;
import org.knowledgeroot.app.i18n.UiMessages;
import org.knowledgeroot.app.notification.NotificationRepository.Notification;
import org.knowledgeroot.app.notification.NotificationRepository.Preference;
import org.knowledgeroot.app.notification.NotificationRepository.Recipient;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageCommented;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PageMoved;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.page.domain.PagePublished;
import org.knowledgeroot.app.security.recovery.RecoverySettings;
import org.knowledgeroot.app.space.Space;
import org.knowledgeroot.app.space.SpaceService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watching and notifications (R66). After a publication, comment or move the watchers of the page and
 * of its space get an entry in their history and, depending on their preference, a mail right away or
 * in the next summary; a mention {@code @login} in a comment reaches the named user. Recipients must
 * still be allowed to read the page when the event happens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    public static final String PUBLISHED = "published";
    public static final String COMMENT = "comment";
    public static final String MENTION = "mention";
    public static final String MOVED = "moved";
    public static final int PAGE_SIZE = 50;
    static final int MAX_MENTIONS = 20;
    static final int EXCERPT = 200;
    /** {@code @login} not glued to a word; logins allow letters, digits, dots, underscores and dashes. */
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<![\\w.@/-])@([A-Za-z0-9][A-Za-z0-9._-]{1,63})");
    private final NotificationRepository notifications;
    private final WatchRepository watches;
    private final MailQueue mails;
    private final NotificationSettings settings;
    private final PageDao pages;
    private final PagePermissionDao permissions;
    private final SpaceService spaces;
    private final UiMessages messages;

    // --- watching -------------------------------------------------------------------------------------

    public void watchPage(int userId, int pageId) {
        requireReadable(userId, pageId);
        watches.watchPage(userId, pageId);
    }

    public void unwatchPage(int userId, int pageId) {
        watches.unwatchPage(userId, pageId);
    }

    public boolean watchesPage(int userId, int pageId) {
        return watches.watchesPage(userId, pageId);
    }

    public void watchSpace(int userId, int spaceId) {
        spaces.requireView(spaceId);
        watches.watchSpace(userId, spaceId);
    }

    public void unwatchSpace(int userId, int spaceId) {
        watches.unwatchSpace(userId, spaceId);
    }

    public boolean watchesSpace(int userId, int spaceId) {
        return watches.watchesSpace(userId, spaceId);
    }

    public List<WatchRepository.WatchedPage> watchedPages(int userId) {
        return watches.watchedPages(userId);
    }

    public List<WatchRepository.WatchedSpace> watchedSpaces(int userId) {
        return watches.watchedSpaces(userId);
    }

    private void requireReadable(int userId, int pageId) {
        Page page;
        try {
            page = pages.findById(new PageId(pageId));
        } catch (EmptyResultDataAccessException e) {
            page = null;
        }
        if (page == null || Boolean.TRUE.equals(page.getDeleted())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found.");
        if (!permissions.hasUserPermission(new PageId(pageId), userId, PagePermission.PermissionLevel.VIEW))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this page.");
    }

    // --- history and preference ---------------------------------------------------------------------

    public List<Notification> history(int userId, int start) {
        return notifications.listForUser(userId, start, PAGE_SIZE);
    }

    public int unread(int userId) {
        return notifications.countUnread(userId);
    }

    public void markRead(int userId) {
        notifications.markAllRead(userId);
    }

    public Preference preference(int userId) {
        return notifications.preference(userId);
    }

    public void savePreference(int userId, String frequency, boolean mentions) {
        NotificationFrequency parsed;
        try {
            parsed = NotificationFrequency.fromValue(frequency);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose how often you want notification mails.");
        }
        notifications.savePreference(userId, parsed, mentions);
    }

    public boolean mailsEnabled() {
        return settings.isEnabled();
    }

    // --- events ---------------------------------------------------------------------------------------

    @TransactionalEventListener(fallbackExecution = true)
    public void onPublished(PagePublished event) {
        Page page = page(event.pageId());
        if (page == null) return;
        String actor = login(event.actorId());
        for (int user : watches.watchersOf(page.getPageId().value(), page.getSpaceId(), event.actorId())) {
            if (!canRead(user, page)) continue;
            notify(user, PUBLISHED, page, event.actorId(), actor, null);
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onCommented(PageCommented event) {
        Page page = page(event.pageId());
        if (page == null) return;
        String actor = login(event.actorId());
        String excerpt = excerpt(event.text());
        Set<Integer> told = new LinkedHashSet<>();
        for (Recipient mentioned : notifications.byLogins(mentions(event.text()))) {
            if (event.actorId() != null && mentioned.id() == event.actorId()) continue;
            if (!canRead(mentioned.id(), page) || !notifications.preference(mentioned.id()).mentions()) continue;
            told.add(mentioned.id());
            notify(mentioned.id(), MENTION, page, event.actorId(), actor, excerpt);
        }
        for (int user : watches.watchersOf(page.getPageId().value(), page.getSpaceId(), event.actorId())) {
            if (told.contains(user) || !canRead(user, page)) continue;
            notify(user, COMMENT, page, event.actorId(), actor, excerpt);
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onMoved(PageMoved event) {
        Page page = page(event.pageId());
        if (page == null) return;
        String actor = login(event.actorId());
        String destination;
        if (event.toParent() > 0) {
            Page parent = page(new PageId(event.toParent()));
            destination = parent == null ? "#" + event.toParent() : parent.getName();
        } else {
            destination = "/" + spaces.spaces().find(event.toSpace()).map(Space::getKey).orElse(String.valueOf(event.toSpace()));
        }
        // Watchers of the old and the new space both learn about the move.
        Set<Integer> users = new LinkedHashSet<>(watches.watchersOf(page.getPageId().value(), event.fromSpace(), event.actorId()));
        users.addAll(watches.watchersOf(page.getPageId().value(), event.toSpace(), event.actorId()));
        for (int user : users) {
            if (!canRead(user, page)) continue;
            notify(user, MOVED, page, event.actorId(), actor, destination);
        }
    }

    /** Logins mentioned as {@code @login}, in order, without duplicates and at most {@link #MAX_MENTIONS}. */
    static List<String> mentions(String text) {
        var names = new LinkedHashSet<String>();
        if (text == null) return List.of();
        Matcher matcher = MENTION_PATTERN.matcher(text);
        while (matcher.find() && names.size() < MAX_MENTIONS) {
            String name = matcher.group(1);
            while (name.endsWith(".") || name.endsWith("-") || name.endsWith("_")) name = name.substring(0, name.length() - 1);
            if (name.length() >= 2) names.add(name.toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(names);
    }

    private static String excerpt(String text) {
        if (text == null) return null;
        String single = text.replaceAll("\\s+", " ").strip();
        return single.length() > EXCERPT ? single.substring(0, EXCERPT - 1) + "…" : single;
    }

    private Page page(PageId id) {
        try {
            Page page = pages.findById(id);
            return page == null || Boolean.TRUE.equals(page.getDeleted()) ? null : page;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private boolean canRead(int userId, Page page) {
        return permissions.hasUserPermission(page.getPageId(), userId, PagePermission.PermissionLevel.VIEW);
    }

    private String login(Integer userId) {
        if (userId == null) return null;
        return notifications.recipient(userId).map(Recipient::login).orElse(null);
    }

    /** Writes the history entry and, for users who want mails right away, queues the mail. */
    private void notify(int userId, String kind, Page page, Integer actorId, String actorLogin, String detail) {
        Preference preference = notifications.preference(userId);
        boolean immediate = preference.frequency() == NotificationFrequency.IMMEDIATE;
        boolean mailed = !settings.isEnabled() || preference.frequency() == NotificationFrequency.NEVER || immediate;
        int id = notifications.create(userId, kind, page.getPageId().value(), page.getName(), actorId, actorLogin, detail, mailed);
        if (immediate && settings.isEnabled()) {
            Notification entry = new Notification(id, userId, kind, page.getPageId().value(), page.getName(), actorId, actorLogin, detail,
                    LocalDateTime.now(), null, true);
            notifications.recipient(userId).ifPresent(recipient -> mailOne(recipient, entry));
        }
    }

    // --- mails ----------------------------------------------------------------------------------------

    private void mailOne(Recipient recipient, Notification entry) {
        String email = RecoverySettings.email(recipient.email());
        if (email == null) return;
        Locale locale = locale(recipient);
        String actor = actor(entry, locale);
        String subject = switch (entry.kind()) {
            case COMMENT -> messages.get(locale, "mail.notification.subject.comment", entry.pageName());
            case MENTION -> messages.get(locale, "mail.notification.subject.mention", actor, entry.pageName());
            case MOVED -> messages.get(locale, "mail.notification.subject.moved", entry.pageName());
            default -> messages.get(locale, "mail.notification.subject.published", entry.pageName());
        };
        var body = new StringBuilder(line(entry, locale)).append("\n\n");
        if ((COMMENT.equals(entry.kind()) || MENTION.equals(entry.kind())) && entry.detail() != null) body.append(entry.detail()).append("\n\n");
        if (entry.pageId() != null) body.append(messages.get(locale, "mail.notification.link", settings.pageUrl(entry.pageId()))).append("\n\n");
        body.append(messages.get(locale, "mail.notification.footer", settings.getPublicUrl() + "/profile"));
        mails.enqueue(recipient.id(), settings.getFrom(), email, subject, body.toString());
    }

    /** Hourly and daily summaries: one mail per user with everything since the last one. */
    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT5M")
    public void sendDigests() {
        sendDigests(LocalDateTime.now());
    }

    public void sendDigests(LocalDateTime now) {
        for (int userId : notifications.usersWithUnmailed()) {
            try {
                Preference preference = notifications.preference(userId);
                List<Notification> pending = notifications.unmailed(userId);
                if (!preference.frequency().digest() || !settings.isEnabled()) {
                    notifications.markMailed(pending.stream().map(Notification::id).toList());
                    continue;
                }
                if (preference.lastDigest() != null && preference.lastDigest().plus(preference.frequency().interval()).isAfter(now)) continue;
                var recipient = notifications.recipient(userId);
                String email = recipient.map(r -> RecoverySettings.email(r.email())).orElse(null);
                if (email != null) {
                    Locale locale = locale(recipient.get());
                    var body = new StringBuilder(messages.get(locale, "mail.digest.intro")).append("\n\n");
                    for (Notification entry : pending) {
                        body.append("- ").append(line(entry, locale));
                        if (entry.pageId() != null) body.append(" ").append(settings.pageUrl(entry.pageId()));
                        body.append('\n');
                    }
                    body.append('\n').append(messages.get(locale, "mail.notification.footer", settings.getPublicUrl() + "/profile"));
                    mails.enqueue(userId, settings.getFrom(), email, messages.get(locale, "mail.digest.subject", pending.size()), body.toString());
                }
                notifications.markMailed(pending.stream().map(Notification::id).toList());
                notifications.markDigest(userId, now);
            } catch (RuntimeException e) {
                log.warn("Notification summary for user {} failed: {}", userId, e.getMessage());
            }
        }
    }

    @Scheduled(initialDelayString = "PT15M", fixedDelayString = "PT6H")
    public void cleanup() {
        int removed = notifications.deleteOlderThan(LocalDateTime.now().minusDays(NotificationRepository.RETENTION_DAYS));
        if (removed > 0) log.info("Removed {} notifications older than {} days", removed, NotificationRepository.RETENTION_DAYS);
    }

    /** The one-line text of an entry, the same in the history and in mails. */
    public String line(Notification entry, Locale locale) {
        String actor = actor(entry, locale);
        return switch (entry.kind()) {
            case COMMENT -> messages.get(locale, "notification.comment", actor, entry.pageName());
            case MENTION -> messages.get(locale, "notification.mention", actor, entry.pageName());
            case MOVED -> messages.get(locale, "notification.moved", actor, entry.pageName(), destination(entry, locale));
            default -> messages.get(locale, "notification.published", actor, entry.pageName());
        };
    }

    private String actor(Notification entry, Locale locale) {
        return entry.actorLogin() == null ? messages.get(locale, "notification.someone") : entry.actorLogin();
    }

    private String destination(Notification entry, Locale locale) {
        String detail = entry.detail() == null ? "" : entry.detail();
        return detail.startsWith("/") ? messages.get(locale, "notification.moved.top-level", detail.substring(1)) : detail;
    }

    private static Locale locale(Recipient recipient) {
        return Languages.find(recipient.language()).map(Languages.Language::locale).orElse(Languages.DEFAULT.locale());
    }
}
