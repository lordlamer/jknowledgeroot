package org.knowledgeroot.app.file.db;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.file.domain.FileFilter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class FileImpl implements FileDao {
    private final JdbcClient jdbcClient;

    @Override
    public List<File> listFiles(FileFilter fileFilter) {
        return null;
    }

    @Override
    public File findById(long id) {
        return null;
    }

    @Override
    public void createFile(MultipartFile file, Integer parentContent) {

    }

    @Override
    public void deleteFileById(long id) {

    }

    @Override
    public void deleteAllFiles() {

    }
}
