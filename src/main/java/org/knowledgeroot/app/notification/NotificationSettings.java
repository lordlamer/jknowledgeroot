package org.knowledgeroot.app.notification;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.knowledgeroot.app.security.recovery.RecoverySettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Switch and addresses of the notification mails (R66): {@code knowledgeroot.notifications.*}, filled
 * from {@code KR_NOTIFICATIONS_ENABLED}, {@code KR_PUBLIC_URL} and {@code KR_MAIL_FROM}. Off, pages
 * and spaces can still be watched and the history fills, only no mail leaves.
 */
@Component
@ConfigurationProperties("knowledgeroot.notifications")
@Getter
@Setter
public class NotificationSettings {
    private boolean enabled;
    private String publicUrl = "";
    private String from = "";

    @PostConstruct
    void validate() {
        if (!enabled) return;
        try {
            var uri = URI.create(publicUrl);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/")) || RecoverySettings.email(from) == null)
                throw new IllegalArgumentException();
            publicUrl = publicUrl.replaceAll("/+$", "");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Notifications require an HTTPS origin (KR_PUBLIC_URL) and a valid sender address (KR_MAIL_FROM)");
        }
    }

    public String pageUrl(int pageId) {
        return publicUrl + "/ui/page/" + pageId;
    }
}
