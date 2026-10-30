package org.knowledgeroot.app.api;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Timestamps are stored in the server's local time. Clients in other time zones need the offset,
 * so the API writes them as ISO-8601 date-times with offset, e.g. 2026-10-25T10:15:00+01:00.
 */
class ServerTime extends ValueSerializer<LocalDateTime> {
    @Override
    public void serialize(LocalDateTime value, JsonGenerator generator, SerializationContext context) {
        generator.writeString(value.atZone(ZoneId.systemDefault()).toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}
