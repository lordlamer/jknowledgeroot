package org.knowledgeroot.app.security.mfa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.stereotype.Service;

/** Reads and changes the second-factor policy and answers who must enrol. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MfaSettings {
    static final String POLICY = "mfa.policy";
    private final AppSettings settings;

    public MfaPolicy policy() {
        var stored = settings.get(POLICY);
        if (stored.isEmpty()) return MfaPolicy.OFF;
        try {
            return MfaPolicy.fromValue(stored.get());
        } catch (IllegalArgumentException e) {
            // A corrupted setting must not lock anybody out or silently disable enrolled tokens.
            log.error("Stored MFA policy is unknown; behaving as 'optional' until an administrator saves a policy");
            return MfaPolicy.OPTIONAL;
        }
    }

    public void update(MfaPolicy policy, int actor) {
        settings.set(POLICY, policy.getValue(), actor);
        log.info("MFA policy changed to {} by user {}", policy.getValue(), actor);
    }

    /** Whether enrolled tokens are asked for at sign-in. */
    public boolean enforces() {
        return policy() != MfaPolicy.OFF;
    }

    /** Whether this account must enrol a token before it may use the application. */
    public boolean requires(UserDetails user) {
        if (user == null || user.isGuest()) return false;
        return switch (policy()) {
            case REQUIRED -> true;
            case REQUIRED_FOR_ADMINS -> user.isAdmin();
            case OFF, OPTIONAL -> false;
        };
    }
}
