package org.knowledgeroot.app.file.db;

import org.springframework.beans.factory.annotation.Value;
import org.knowledgeroot.app.file.domain.StorageException;
import org.knowledgeroot.app.file.domain.StoredFileNotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.AccessDeniedException;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * File system storage implementation of the file storage.
 */
class FileSystemStorage implements FileStorage {
    private final Path storageLocation;

    public FileSystemStorage(@Value("${file.storage-dir}") String storageDir) {
        this.storageLocation = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageLocation);
        } catch (IOException ex) {
            throw new StorageException("Could not create storage directory!", ex);
        }
    }

    @Override
    public void store(String hash, InputStream inputStream) {
        Path temporary = null;
        try {
            Path targetLocation = objectPath(hash);
            if (exists(hash)) return;
            temporary = Files.createTempFile(storageLocation, ".upload-", ".tmp");
            Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
            // Readers only see a complete object, including after a failed upload/retry.
            try {
                Files.move(temporary, targetLocation, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException | AccessDeniedException ex) {
                // Another instance published the same content-addressed object first.
                // Windows reports an existing ATOMIC_MOVE destination as AccessDeniedException.
                if (!exists(hash)) throw ex;
            }
        } catch (IOException ex) {
            throw new StorageException("Failed to store file.", ex);
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
            Path file = objectPath(hash);
            return Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException ex) {
            throw new StoredFileNotFoundException(ex);
        } catch (IOException ex) {
            throw new StorageException("Failed to read file.", ex);
        }
    }

    @Override
    public void delete(String hash) {
        try {
            Path file = objectPath(hash);
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new StorageException("Failed to delete file.", ex);
        }
    }

    @Override
    public boolean exists(String hash) {
        try {
            var attributes = Files.readAttributes(objectPath(hash), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) throw new IOException("Storage object is not a regular file");
            return true;
        } catch (NoSuchFileException ex) {
            return false;
        } catch (IOException ex) {
            throw new StorageException("Could not inspect stored file", ex);
        }
    }

    private Path objectPath(String hash) {
        Path path = storageLocation.resolve(StorageKey.validate(hash));
        if (Files.isSymbolicLink(path)) throw new StorageException("Symbolic storage objects are not supported", null);
        return path;
    }

    @Override public void checkAvailability() {
        try {
            Path probe = Files.createTempFile(storageLocation, ".health-", ".tmp");
            try {
                Files.write(probe, new byte[]{42});
                if (Files.readAllBytes(probe)[0] != 42) throw new IOException("Storage probe failed");
            } finally { Files.deleteIfExists(probe); }
        } catch (IOException failure) { throw new StorageException("Storage probe failed", failure); }
    }
}
