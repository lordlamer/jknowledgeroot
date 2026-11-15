package org.knowledgeroot.app.notification;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.util.RequestValidation;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/** The user's notifications, the watch buttons on pages and spaces, and the mail preference in the profile (R66). */
@Controller
@RequiredArgsConstructor
class NotificationController {
    private final NotificationService notifications;
    private final UserContext userContext;

    private int requireUser() {
        UserDetails user = userContext.getUserContext();
        if (user.isGuest()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return Integer.parseInt(user.getUserId());
    }

    /** Opening the history marks everything as read; the badge in the header follows. */
    @GetMapping("/notifications")
    String list(@RequestParam(defaultValue = "0") Integer start, Model model, HtmxRequest htmx) {
        int user = requireUser();
        int offset = RequestValidation.start(start);
        List<NotificationRepository.Notification> entries = notifications.history(user, offset);
        Locale locale = LocaleContextHolder.getLocale();
        model.addAttribute("entries", entries.stream().limit(NotificationService.PAGE_SIZE)
                .map(entry -> new Entry(entry, notifications.line(entry, locale))).toList());
        model.addAttribute("hasNext", entries.size() > NotificationService.PAGE_SIZE);
        model.addAttribute("start", offset);
        model.addAttribute("nextStart", offset + NotificationService.PAGE_SIZE);
        model.addAttribute("watchedPages", notifications.watchedPages(user));
        model.addAttribute("watchedSpaces", notifications.watchedSpaces(user));
        model.addAttribute("preference", notifications.preference(user));
        model.addAttribute("mailsEnabled", notifications.mailsEnabled());
        notifications.markRead(user);
        model.addAttribute("unreadNotifications", 0);
        return htmx.isHtmxRequest() ? "notification/list :: body" : "notification/list";
    }

    public record Entry(NotificationRepository.Notification notification, String text) {}

    @PostMapping("/ui/page/{pageId}/watch")
    String watchPage(@PathVariable int pageId, Model model) {
        notifications.watchPage(requireUser(), pageId);
        return watchButton(pageId, true, model);
    }

    @DeleteMapping("/ui/page/{pageId}/watch")
    String unwatchPage(@PathVariable int pageId, Model model) {
        notifications.unwatchPage(requireUser(), pageId);
        return watchButton(pageId, false, model);
    }

    private static String watchButton(int pageId, boolean watching, Model model) {
        model.addAttribute("pageId", pageId);
        model.addAttribute("watching", watching);
        return "page/watch-button :: button";
    }

    @PostMapping("/spaces/{spaceId}/watch")
    String watchSpace(@PathVariable int spaceId, Model model) {
        notifications.watchSpace(requireUser(), spaceId);
        return spaceButton(spaceId, true, model);
    }

    @DeleteMapping("/spaces/{spaceId}/watch")
    String unwatchSpace(@PathVariable int spaceId, Model model) {
        notifications.unwatchSpace(requireUser(), spaceId);
        return spaceButton(spaceId, false, model);
    }

    private static String spaceButton(int spaceId, boolean watching, Model model) {
        model.addAttribute("spaceId", spaceId);
        model.addAttribute("watchingSpace", watching);
        return "space/watch-button :: button";
    }

    @PostMapping("/profile/notifications")
    String savePreference(@RequestParam String frequency, @RequestParam(required = false) String mentions) {
        notifications.savePreference(requireUser(), frequency, mentions != null);
        return "redirect:/profile?notifications";
    }
}
