package org.knowledgeroot.app.api;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.notification.NotificationRepository;
import org.knowledgeroot.app.notification.NotificationService;
import org.knowledgeroot.app.util.RequestValidation;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

/** Watching pages and spaces, the notification history and the mail preference for app clients (R66). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class NotificationApiController {
    private final ApiSupport api;
    private final NotificationService notifications;

    @GetMapping("/notifications")
    ApiModels.NotificationList list(@RequestParam(defaultValue = "0") int start) {
        int user = api.requireUser();
        int offset = RequestValidation.start(start);
        List<NotificationRepository.Notification> entries = notifications.history(user, offset);
        Locale locale = LocaleContextHolder.getLocale();
        var items = entries.stream().limit(NotificationService.PAGE_SIZE).map(entry -> new ApiModels.NotificationEntry(entry.id(), entry.kind(),
                entry.pageId(), entry.pageName(), entry.actorLogin(), entry.detail(), notifications.line(entry, locale), entry.createDate(), entry.unread())).toList();
        return new ApiModels.NotificationList(items, offset, entries.size() > NotificationService.PAGE_SIZE, notifications.unread(user));
    }

    /** Marks every notification as read. */
    @PostMapping("/notifications/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead() {
        notifications.markRead(api.requireUser());
    }

    @PutMapping("/pages/{id}/watch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void watchPage(@PathVariable int id) {
        notifications.watchPage(api.requireUser(), id);
    }

    @DeleteMapping("/pages/{id}/watch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unwatchPage(@PathVariable int id) {
        notifications.unwatchPage(api.requireUser(), id);
    }

    @PutMapping("/spaces/{id}/watch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void watchSpace(@PathVariable int id) {
        notifications.watchSpace(api.requireUser(), id);
    }

    @DeleteMapping("/spaces/{id}/watch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unwatchSpace(@PathVariable int id) {
        notifications.unwatchSpace(api.requireUser(), id);
    }

    @GetMapping("/profile/notifications")
    ApiModels.NotificationPreference preference() {
        return preference(api.requireUser());
    }

    @PutMapping("/profile/notifications")
    ApiModels.NotificationPreference savePreference(@RequestBody ApiModels.NotificationPreference input) {
        int user = api.requireUser();
        notifications.savePreference(user, input.frequency(), !Boolean.FALSE.equals(input.mentions()));
        return preference(user);
    }

    private ApiModels.NotificationPreference preference(int user) {
        var preference = notifications.preference(user);
        return new ApiModels.NotificationPreference(preference.frequency().value(), preference.mentions(), notifications.mailsEnabled());
    }
}
