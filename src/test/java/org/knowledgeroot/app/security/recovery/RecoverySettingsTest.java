package org.knowledgeroot.app.security.recovery;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoverySettingsTest {
    @Test void enabledRecoveryRequiresAnHttpsOriginAndASingleSenderAddress() {
        for (String url : new String[]{"", "http://example.org", "https://example.org@evil.example", "https://example.org/path",
                "https://example.org?host=other", "https://example.org/#fragment"}) {
            var settings = new RecoverySettings(); settings.setEnabled(true);
            settings.setFrom("sender@example.org"); settings.setPublicUrl(url);
            assertThrows(IllegalStateException.class, settings::validate, url);
        }
        var valid = new RecoverySettings(); valid.setEnabled(true);
        valid.setPublicUrl("https://knowledge.example/"); valid.setFrom("sender@example.org");
        valid.validate(); assertEquals("https://knowledge.example", valid.getPublicUrl());
        assertDoesNotThrow(new RecoverySettings()::validate);
    }

    @Test void emailNormalizationRejectsHeadersListsDisplayNamesAndMalformedAddresses() {
        assertEquals("person@example.org", RecoverySettings.email(" Person@Example.org "));
        for (String email : new String[]{"", "bad", "@example.org", "user@", "a@example.org,b@example.org",
                "Name <user@example.org>", "a@example.org\r\nBcc: b@example.org", "a b@example.org"})
            assertNull(RecoverySettings.email(email), email);
        assertNull(RecoverySettings.email(null));
    }
}
