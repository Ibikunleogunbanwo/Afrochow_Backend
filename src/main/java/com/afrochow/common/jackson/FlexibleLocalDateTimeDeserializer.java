package com.afrochow.common.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Accepts both date-only ("2026-03-24") and full datetime ("2026-03-24T00:00:00")
 * strings for LocalDateTime fields.
 * Date-only values are treated as the start of that day (T00:00:00).
 */
public class FlexibleLocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {

    public FlexibleLocalDateTimeDeserializer() {
        super(LocalDateTime.class);
    }

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        return parse(p.getText());
    }

    /** Parse a date-only or full datetime string into LocalDateTime. */
    public static LocalDateTime parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        raw = raw.trim();
        if (raw.length() == 10) {
            return LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        }
        return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
