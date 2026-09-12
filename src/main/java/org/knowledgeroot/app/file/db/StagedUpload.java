package org.knowledgeroot.app.file.db;

import org.knowledgeroot.app.file.domain.UploadPolicy;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Hash and bound the actual bytes once; store exactly those bytes, independently of the request stream. */
final class StagedUpload implements AutoCloseable {
    final Path path;
    final String key;
    final long size;

    private StagedUpload(Path path, String key, long size) {
        this.path = path;
        this.key = key;
        this.size = size;
    }

    static StagedUpload read(MultipartFile file, long maximum) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        Path temporary = Files.createTempFile("knowledgeroot-upload-", ".tmp");
        boolean complete = false;
        try {
            long size = 0;
            try (var input = file.getInputStream(); var output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > maximum) UploadPolicy.tooLarge();
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
            }
            if (size == 0 || size != file.getSize()) UploadPolicy.badRequest();
            StagedUpload result = new StagedUpload(temporary, "sha256-" + HexFormat.of().formatHex(digest.digest()), size);
            complete = true;
            return result;
        } finally {
            if (!complete) Files.deleteIfExists(temporary);
        }
    }

    @Override public void close() throws IOException { Files.deleteIfExists(path); }
}
