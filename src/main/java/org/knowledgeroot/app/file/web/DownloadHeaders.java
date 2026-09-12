package org.knowledgeroot.app.file.web;

import jakarta.servlet.http.HttpServletResponse;
import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.file.domain.UploadPolicy;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;

public final class DownloadHeaders {
    private DownloadHeaders() {}
    public static void apply(HttpServletResponse response, File file) {
        String filename;
        try { filename = UploadPolicy.name(file.getName()); }
        catch (RuntimeException ex) { filename = "download"; }
        String type = "application/octet-stream";
        try {
            if (file.getType() != null && file.getType().length() <= 255
                    && file.getType().chars().noneMatch(Character::isISOControl)) {
                var parsed = MediaType.parseMediaType(file.getType());
                if (!parsed.isWildcardType() && !parsed.isWildcardSubtype()) type = parsed.toString();
            }
        } catch (IllegalArgumentException ignored) { }
        response.setContentType(type);
        response.setHeader("Content-Disposition", ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "private, no-store");
        if (file.getSize() != null && file.getSize() >= 0) response.setContentLengthLong(file.getSize());
    }
}
