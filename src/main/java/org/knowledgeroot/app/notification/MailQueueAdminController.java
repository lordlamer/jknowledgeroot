package org.knowledgeroot.app.notification;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Admin → Mail: the queue with waiting, failed and sent mails; retry or delete single mails (R66). */
@Controller
@RequiredArgsConstructor
class MailQueueAdminController {
    private static final int LIMIT = 100;
    private final MailQueue mails;
    private final NotificationSettings settings;

    @GetMapping("/admin/mail")
    String show(@RequestParam(required = false) String retried, @RequestParam(required = false) String deleted, Model model, HtmxRequest htmx) {
        model.addAttribute("counts", mails.counts());
        model.addAttribute("mails", mails.list(LIMIT));
        model.addAttribute("limit", LIMIT);
        model.addAttribute("maxAttempts", MailQueue.MAX_ATTEMPTS);
        model.addAttribute("retentionDays", MailQueue.SENT_RETENTION_DAYS);
        model.addAttribute("notificationsEnabled", settings.isEnabled());
        model.addAttribute("retried", retried != null);
        model.addAttribute("deleted", deleted != null);
        return htmx.isHtmxRequest() ? "admin/mail :: body" : "admin/mail";
    }

    @PostMapping("/admin/mail/{id}/retry")
    String retry(@PathVariable int id) {
        if (!mails.retry(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mail not found.");
        return "redirect:/admin/mail?retried";
    }

    @PostMapping("/admin/mail/{id}/delete")
    String delete(@PathVariable int id) {
        if (!mails.delete(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mail not found.");
        return "redirect:/admin/mail?deleted";
    }
}
