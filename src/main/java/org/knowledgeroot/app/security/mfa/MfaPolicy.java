package org.knowledgeroot.app.security.mfa;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Administrator-chosen second-factor policy, stored in {@code app_setting}. */
@Getter
@RequiredArgsConstructor
public enum MfaPolicy {
    /** No second step at all; enrolled tokens are kept but not asked for. */
    OFF("off"),
    /** Users who enrolled a token in their profile are asked for it; others sign in with the password only. */
    OPTIONAL("optional"),
    /** Administrators must enrol and verify; other users as in {@link #OPTIONAL}. */
    REQUIRED_FOR_ADMINS("required-for-admins"),
    /** Every user must enrol at the next sign-in and verify afterwards. */
    REQUIRED("required");

    private final String value;

    public static MfaPolicy fromValue(String value) {
        for (MfaPolicy policy : values()) if (policy.value.equals(value)) return policy;
        throw new IllegalArgumentException("Unknown MFA policy");
    }
}
