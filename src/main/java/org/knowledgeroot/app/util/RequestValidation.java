package org.knowledgeroot.app.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

public final class RequestValidation {
    private RequestValidation() {}
    public static int start(Integer value) {
        int start = value == null ? 0 : value;
        require(start >= 0 && start <= 100_000);
        return start;
    }
    public static int limit(Integer value) {
        int limit = value == null ? 50 : value;
        require(limit >= 1 && limit <= 100);
        return limit;
    }
    public static void text(String value, int max) { require(value == null || value.length() <= max); }
    public static void id(Integer value) { require(value == null || value > 0); }
    public static void range(LocalDateTime begin, LocalDateTime end) {
        require(begin == null || end == null || !begin.isAfter(end));
    }
    public static void require(boolean condition) {
        if (!condition) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid input");
    }
}
