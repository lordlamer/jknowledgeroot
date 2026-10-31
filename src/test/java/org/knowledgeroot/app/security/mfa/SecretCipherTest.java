package org.knowledgeroot.app.security.mfa;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SecretCipherTest {
    private static final String KEY = "unit-test-master-key-with-enough-length-0123456789";

    @Test void roundTripsWithRandomIvAndRejectsOtherKeys() {
        var cipher = new SecretCipher(KEY);
        byte[] first = cipher.encrypt("GEZDGNBVGY3TQOJQ"), second = cipher.encrypt("GEZDGNBVGY3TQOJQ");
        assertFalse(Arrays.equals(first, second), "IVs must differ");
        assertEquals("GEZDGNBVGY3TQOJQ", cipher.decrypt(first));
        assertEquals("GEZDGNBVGY3TQOJQ", cipher.decrypt(second));
        assertThrows(IllegalStateException.class, () -> new SecretCipher(KEY + "x").decrypt(first));
        first[first.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(first));
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(new byte[10]));
    }

    @Test void keyedHashIsStableAndKeyDependent() {
        var cipher = new SecretCipher(KEY);
        assertEquals(cipher.hmac("abcd-efgh"), cipher.hmac("abcd-efgh"));
        assertTrue(cipher.hmac("abcd-efgh").matches("[0-9a-f]{64}"));
        assertNotEquals(cipher.hmac("abcd-efgh"), cipher.hmac("abcd-efgi"));
        assertNotEquals(cipher.hmac("abcd-efgh"), new SecretCipher(KEY + "x").hmac("abcd-efgh"));
    }

    @Test void blankKeyIsRefusedInProductionOnly() {
        var production = new MockEnvironment();
        production.setActiveProfiles("production");
        assertThrows(IllegalStateException.class, () -> new SecretCipher(" ", production));
        var development = new SecretCipher("", new MockEnvironment());
        assertEquals("x", development.decrypt(development.encrypt("x")));
        assertThrows(IllegalStateException.class, () -> new SecretCipher(""));
    }
}
