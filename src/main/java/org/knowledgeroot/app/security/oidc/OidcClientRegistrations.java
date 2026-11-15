package org.knowledgeroot.app.security.oidc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

/**
 * The one client registration of the configured provider (R64), built from its discovery document
 * on first use rather than at startup, so the application starts even while the provider is down.
 * Without an issuer there is no registration and every OIDC path answers as if it did not exist.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OidcClientRegistrations implements ClientRegistrationRepository {
    public static final String ID = "oidc";
    /** Where the login page sends the browser; the controller checks the provider first. */
    public static final String START_PATH = "/login/oidc";
    public static final String AUTHORIZATION_PATH = "/oauth2/authorization/" + ID;

    private final OidcSettings settings;
    private volatile ClientRegistration cached;

    public boolean enabled() {
        return settings.enabled();
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        if (!ID.equals(registrationId) || !settings.enabled()) return null;
        ClientRegistration registration = cached;
        if (registration != null) return registration;
        synchronized (this) {
            if (cached == null) cached = build();
            return cached;
        }
    }

    /** False when the provider's discovery document cannot be read right now; the cause is logged. */
    public boolean available() {
        try {
            return findByRegistrationId(ID) != null;
        } catch (RuntimeException ex) {
            log.warn("OpenID Connect provider {} is not reachable: {}", settings.getIssuer(), ex.getMessage());
            return false;
        }
    }

    private ClientRegistration build() {
        var builder = ClientRegistrations.fromIssuerLocation(settings.getIssuer())
                .registrationId(ID)
                .clientId(settings.getClientId())
                .clientName(settings.getName())
                .scope(settings.scopeList());
        if (settings.getClientSecret().isEmpty()) {
            // A public client: Spring Security then adds PKCE to the authorization request.
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        } else {
            builder.clientSecret(settings.getClientSecret());
        }
        var registration = builder.build();
        log.info("OpenID Connect provider {} registered for sign-in", settings.getIssuer());
        return registration;
    }
}
