package org.knowledgeroot.app.file.db;

import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.file.domain.FileFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * File data access object implementation.
 */
@Repository
public class FileImpl implements FileDao {
    private final JdbcTemplate jdbcTemplate;
    private final FileStorage fileStorage;

    private final RowMapper<File> fileMapper = (rs, rowNum) -> {
        File file = new File();
        file.setId(rs.getInt("id"));
        file.setParent(rs.getInt("parent"));
        file.setHash(rs.getString("hash"));
        file.setName(rs.getString("name"));
        file.setSize(rs.getInt("size"));
        file.setType(rs.getString("type"));
        file.setDownloads(rs.getInt("downloads"));
        file.setCreatedBy(rs.getInt("created_by"));
        file.setCreateDate(rs.getTimestamp("create_date").toLocalDateTime());
        file.setChangedBy(rs.getInt("changed_by"));
        file.setChangeDate(rs.getTimestamp("change_date").toLocalDateTime());
        file.setDeleted(rs.getBoolean("deleted"));
        return file;
    };

    public FileImpl(JdbcTemplate jdbcTemplate, FileStorage fileStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorage = fileStorage;
    }

    @Override
    public List<File> listFiles(FileFilter fileFilter) {
        String sql = "SELECT * FROM file WHERE deleted = false";
        if (fileFilter != null && fileFilter.getContentId() != null) {
            sql += " AND parent = ?";
            return jdbcTemplate.query(sql, fileMapper, fileFilter.getContentId().value());
        }
        return jdbcTemplate.query(sql, fileMapper);
    }

    @Override
    public File findById(long id) {
        String sql = "SELECT * FROM file WHERE id = ? AND deleted = false";
        return jdbcTemplate.queryForObject(sql, fileMapper, id);
    }

    @Override
    public void createFile(MultipartFile file, Integer parentContent) {
        try {
            // Generate MD5 hash
            MessageDigest md = MessageDigest.getInstance("MD5");
            String hash = DatatypeConverter.printHexBinary(
                    md.digest(file.getBytes())
            ).toLowerCase();

            // Store file content if it doesn't exist
            if (!fileStorage.exists(hash)) {
                fileStorage.store(hash, file.getInputStream());
            }

            // Store metadata in database
            String sql = """
                INSERT INTO file (parent, hash, name, size, type, created_by, create_date, changed_by, change_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            jdbcTemplate.update(sql,
                    parentContent,
                    hash,
                    file.getOriginalFilename(),
                    file.getSize(),
                    file.getContentType(),
                    1, // TODO: Get actual user ID
                    LocalDateTime.now(),
                    1, // TODO: Get actual user ID
                    LocalDateTime.now()
            );
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new RuntimeException("Failed to create file", ex);
        }
    }

    @Override
    public void deleteFileById(long id) {
        String sql = "UPDATE file SET deleted = true WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    @Override
    public void deleteAllFiles() {
        String sql = "UPDATE file SET deleted = true";
        jdbcTemplate.update(sql);
    }

    @Override
    public InputStream loadFile(Integer fileId) {
        try {
            // 1. Abfrage der Datei anhand der ID, um den Hash zu erhalten
            String sql = "SELECT hash FROM file WHERE id = ? AND deleted = false";
            String hash = jdbcTemplate.queryForObject(sql, String.class, fileId);

            if (hash == null) {
                return null;
            }

            // 2. Datei-Inhalt als InputStream über den FileStorage abrufen
            return fileStorage.retrieve(hash);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load file with ID: " + fileId, ex);
        }
    }
}
