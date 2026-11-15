package org.knowledgeroot.app.notification;

import java.time.Duration;
import java.util.Locale;

/** How often a user wants notification mails; the in-app history is kept in every case. */
public enum NotificationFrequency {
    IMMEDIATE(Duration.ZERO), HOURLY(Duration.ofHours(1)), DAILY(Duration.ofDays(1)), NEVER(null);

    private final Duration interval;

    NotificationFrequency(Duration interval) {
        this.interval = interval;
    }

    /** The time between two summaries; null for immediate mails and for no mails at all. */
    public Duration interval() {
        return interval;
    }

    public boolean digest() {
        return this == HOURLY || this == DAILY;
    }

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static NotificationFrequency fromValue(String value) {
        for (NotificationFrequency frequency : values()) if (frequency.value().equalsIgnoreCase(value)) return frequency;
        throw new IllegalArgumentException("Unknown frequency");
    }
}
