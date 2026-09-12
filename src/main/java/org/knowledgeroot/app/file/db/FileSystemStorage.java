package org.knowledgeroot.app.file.db;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * File system storage implementation of the file storage.
 */
@Component
class FileSystemStorage implements FileStorage {
    private final Path storageLocation;

    public FileSystemStorage(@Value("${file.storage-dir}") String storageDir) {
        this.storageLocation = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageLocation);
        } catch (IOException ex) {
            throw new RuntimeException("Could not create storage directory!", ex);
        }
    }

    @Override
    public void store(String hash, InputStream inputStream) {
        Path temporary = null;
        try {
            Path targetLocation = storageLocation.resolve(hash);
            if (exists(hash)) return;
            temporary = Files.createTempFile(storageLocation, ".upload-", ".tmp");
            Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
            // Readers only see a complete object, including after a failed upload/retry.
            Files.move(temporary, targetLocation, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to store file.", ex);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ex) {
                    org.slf4j.LoggerFactory.getLogger(FileSystemStorage.class)
                            .warn("Could not remove temporary upload {}", temporary, ex);
                }
            }
        }
    }

    @Override
    public InputStream retrieve(String hash) {
        try {
            Path file = storageLocation.resolve(hash);
            return Files.newInputStream(file);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read file.", ex);
        }
    }

    @Override
    public void delete(String hash) {
        try {
            Path file = storageLocation.resolve(hash);
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to delete file.", ex);
        }
    }

    @Override
    public boolean exists(String hash) {
        return Files.exists(storageLocation.resolve(hash));
    }
}
