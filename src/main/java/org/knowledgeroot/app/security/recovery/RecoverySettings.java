package org.knowledgeroot.app.security.recovery;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.InternetAddress;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Locale;

@Component
@ConfigurationProperties("knowledgeroot.recovery")
@Getter
@Setter
public class RecoverySettings {
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
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/")) || email(from) == null)
                throw new IllegalArgumentException();
            publicUrl = publicUrl.replaceAll("/+$", "");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Account recovery requires an HTTPS origin and a valid sender address");
        }
    }

    public static String email(String value) {
        if (value == null || value.length() > 255) return null;
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.chars().anyMatch(c -> c <= 32 || c >= 127)) return null;
        try {
            var address = new InternetAddress(normalized, true);
            address.validate();
            return address.getPersonal() == null && normalized.equals(address.getAddress())
                    && normalized.contains("@") ? normalized : null;
        } catch (jakarta.mail.internet.AddressException ex) { return null; }
    }
}
