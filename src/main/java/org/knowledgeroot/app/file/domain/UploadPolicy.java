package org.knowledgeroot.app.file.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UploadPolicy {
    private final long maxFileBytes;
    private final long maxRequestBytes;
    private final int maxFiles;

    public UploadPolicy(@Value("${spring.servlet.multipart.max-file-size:25MB}") String fileSize,
                        @Value("${spring.servlet.multipart.max-request-size:100MB}") String requestSize,
                        @Value("${knowledgeroot.upload.max-files:10}") int maxFiles) {
        this.maxFileBytes = DataSize.parse(fileSize).toBytes();
        this.maxRequestBytes = DataSize.parse(requestSize).toBytes();
        this.maxFiles = maxFiles;
        if (maxFileBytes <= 0 || maxFileBytes > Integer.MAX_VALUE || maxRequestBytes < maxFileBytes || maxFiles < 1 || maxFiles > 100) {
            throw new IllegalArgumentException("Invalid upload limits");
        }
    }

    public long maxFileBytes() { return maxFileBytes; }

    public void validate(MultipartFile[] uploads) {
        if (uploads == null || uploads.length == 0) badRequest();
        if (uploads.length > maxFiles) tooLarge();
        long total = 0;
        for (var file : uploads) {
            if (file == null || file.getSize() <= 0 || file.isEmpty()) badRequest();
            if (file.getSize() > maxFileBytes) tooLarge();
            total += file.getSize();
            if (total > maxRequestBytes) tooLarge();
            name(file.getOriginalFilename());
            String type = file.getContentType();
            if (type != null && !type.isBlank()) {
                if (type.length() > 255 || type.chars().anyMatch(Character::isISOControl)) badRequest();
                try {
                    var mediaType = MediaType.parseMediaType(type);
                    if (mediaType.isWildcardType() || mediaType.isWildcardSubtype()) badRequest();
                } catch (IllegalArgumentException ex) { badRequest(); }
            }
        }
    }

    public static String name(String original) {
        if (original == null || original.length() > 4096 || original.chars().anyMatch(Character::isISOControl)) badRequest();
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.equals(".") || name.equals("..") || name.length() > 255) badRequest();
        return name;
    }

    public static void tooLarge() { throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Upload limit exceeded"); }
    public static void badRequest() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid upload"); }
}
