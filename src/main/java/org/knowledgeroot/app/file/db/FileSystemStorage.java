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
        try {
            Path targetLocation = storageLocation.resolve(hash);
            if (!exists(hash)) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to store file.", ex);
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
