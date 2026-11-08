package org.knowledgeroot.app.security.oidc;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Sign-in through an OpenID Connect provider (R64): {@code knowledgeroot.oidc.*}, filled from the
 * {@code KR_OIDC_*} environment variables. An empty issuer leaves the feature off.
 */
@Component
@ConfigurationProperties("knowledgeroot.oidc")
@Getter
@Setter
public class OidcSettings {
    private String issuer = "";
    private String clientId = "";
    /** Empty for a public client: the authorization code is then protected with PKCE instead. */
    private String clientSecret = "";
    private String scopes = "openid,profile,email";
    /** Label of the button on the login page. */
    private String name = "Single sign-on";
    /** Claim with the user's groups; empty means groups are not taken over. */
    private String groupsClaim = "";
    /** Group whose members are administrators; needs the groups claim. Empty leaves the admin flag alone. */
    private String adminGroup = "";
    /** Create groups from the claim that do not exist yet; otherwise unknown groups are skipped. */
    private boolean createGroups;

    public boolean enabled() {
        return !issuer.isBlank();
    }

    public List<String> scopeList() {
        return Arrays.stream(scopes.split("[,\\s]+")).map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    }

    @PostConstruct
    void validate() {
        issuer = issuer.strip();
        clientId = clientId.strip();
        clientSecret = clientSecret.strip();
        name = name.isBlank() ? "Single sign-on" : name.strip();
        groupsClaim = groupsClaim.strip();
        adminGroup = adminGroup.strip();
        if (!enabled()) return;
        URI uri;
        try {
            uri = URI.create(issuer);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("knowledgeroot.oidc.issuer (KR_OIDC_ISSUER) is not a valid URL");
        }
        if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())))
            throw new IllegalStateException("knowledgeroot.oidc.issuer (KR_OIDC_ISSUER) needs an http(s) URL with a host");
        if (clientId.isEmpty())
            throw new IllegalStateException("knowledgeroot.oidc.client-id (KR_OIDC_CLIENT_ID) is required when an OIDC issuer is set");
        if (!scopeList().contains("openid"))
            throw new IllegalStateException("knowledgeroot.oidc.scopes (KR_OIDC_SCOPES) must include openid");
        if (!adminGroup.isEmpty() && groupsClaim.isEmpty())
            throw new IllegalStateException("knowledgeroot.oidc.admin-group (KR_OIDC_ADMIN_GROUP) needs knowledgeroot.oidc.groups-claim (KR_OIDC_GROUPS_CLAIM)");
    }
}
