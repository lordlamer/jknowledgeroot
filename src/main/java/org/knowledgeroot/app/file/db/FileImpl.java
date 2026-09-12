package org.knowledgeroot.app.file.db;

import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.file.domain.FileFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;

import org.knowledgeroot.app.file.domain.UploadPolicy;
import org.knowledgeroot.app.file.domain.StorageException;
import java.nio.file.Files;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * File data access object implementation.
 */
@Repository
public class FileImpl implements FileDao {
    private final JdbcTemplate jdbcTemplate;
    private final FileStorage fileStorage;
    private final UploadPolicy policy;

    /**
     * Row mapper for file entity.
     */
    private final RowMapper<File> fileMapper = (rs, rowNum) -> {
        File file = new File();
        file.setId(rs.getInt("id"));
        file.setPageId(rs.getInt("page_id"));
        file.setHash(rs.getString("hash"));
        file.setName(rs.getString("name"));
        file.setSize(rs.getInt("size"));
        file.setType(rs.getString("type"));
        file.setDownloads(rs.getInt("downloads"));
        file.setCreatedBy(rs.getObject("created_by", Integer.class));
        file.setCreateDate(rs.getTimestamp("create_date").toLocalDateTime());
        file.setChangedBy(rs.getObject("changed_by", Integer.class));
        file.setChangeDate(rs.getTimestamp("change_date").toLocalDateTime());
        file.setDeleted(rs.getBoolean("deleted"));
        return file;
    };

    /**
     * Constructor.
     *
     * @param jdbcTemplate JDBC template
     * @param fileStorage  file storage
     */
    public FileImpl(JdbcTemplate jdbcTemplate, FileStorage fileStorage, UploadPolicy policy) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorage = fileStorage;
        this.policy = policy;
    }

    @Override
    public List<File> listFiles(FileFilter fileFilter) {
        String sql = "SELECT * FROM file WHERE deleted = false";
        if (fileFilter != null && fileFilter.getPageId() != null) {
            sql += " AND page_id = ?";
            return jdbcTemplate.query(sql, fileMapper, fileFilter.getPageId().value());
        }
        return jdbcTemplate.query(sql, fileMapper);
    }

    @Override
    public File findById(long id) {
        String sql = "SELECT * FROM file WHERE id = ? AND deleted = false";
        return jdbcTemplate.queryForObject(sql, fileMapper, id);
    }

    @Override
    public void createFile(MultipartFile file, Integer pageId, Integer actor) {
        policy.validate(new MultipartFile[]{file});
        try (StagedUpload staged = StagedUpload.read(file, policy.maxFileBytes())) {
            if (!fileStorage.exists(staged.key)) {
                try (InputStream input = Files.newInputStream(staged.path)) {
                    fileStorage.store(staged.key, input);
                }
            }

            // Store metadata in database
            String sql = """
                INSERT INTO file (page_id, hash, name, size, type, created_by, create_date, changed_by, change_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            LocalDateTime now = LocalDateTime.now();
            jdbcTemplate.update(sql,
                    pageId,
                    staged.key,
                    UploadPolicy.name(file.getOriginalFilename()),
                    staged.size,
                    file.getContentType() == null || file.getContentType().isBlank()
                            ? "application/octet-stream" : file.getContentType(),
                    actor,
                    now,
                    actor,
                    now
            );
        } catch (IOException ex) {
            throw new StorageException("Failed to stage upload", ex);
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
        String hash = jdbcTemplate.queryForObject("SELECT hash FROM file WHERE id = ? AND deleted = false", String.class, fileId);
        return fileStorage.retrieve(hash);
    }
}
