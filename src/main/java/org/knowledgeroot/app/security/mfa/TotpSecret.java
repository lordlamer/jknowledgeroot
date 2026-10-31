package org.knowledgeroot.app.security.mfa;

import java.security.SecureRandom;

/** Shared TOTP secret (160 bit, Base32). Never appears in {@code toString} or logs. */
public final class TotpSecret {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final String base32;

    private TotpSecret(String base32) {
        this.base32 = base32;
    }

    public static TotpSecret generate() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return new TotpSecret(Base32.encode(bytes));
    }

    /** @throws IllegalArgumentException when the value is not Base32 or shorter than 80 bit */
    public static TotpSecret of(String base32) {
        String value = base32 == null ? "" : base32.replaceAll("\\s", "");
        if (Base32.decode(value).length < 10) throw new IllegalArgumentException("TOTP secret is too short");
        return new TotpSecret(value);
    }

    public String base32() {
        return base32;
    }

    byte[] bytes() {
        return Base32.decode(base32);
    }

    @Override
    public String toString() {
        return "TotpSecret[***]";
    }
}
