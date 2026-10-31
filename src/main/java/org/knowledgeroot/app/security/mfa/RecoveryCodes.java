package org.knowledgeroot.app.security.mfa;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One-time recovery codes in the form {@code xxxx-xxxx} without easily confused characters (i, l, o, 0, 1). */
public final class RecoveryCodes {
    public static final int COUNT = 10;
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private RecoveryCodes() {}

    public static List<String> generate() {
        List<String> codes = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            StringBuilder code = new StringBuilder(9);
            for (int j = 0; j < 8; j++) {
                if (j == 4) code.append('-');
                code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            codes.add(code.toString());
        }
        return codes;
    }

    /** The stored form of an input, or an empty string when it cannot be a recovery code. */
    public static String normalize(String input) {
        String value = input == null ? "" : input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return value.length() == 8 ? value.substring(0, 4) + "-" + value.substring(4) : "";
    }
}
