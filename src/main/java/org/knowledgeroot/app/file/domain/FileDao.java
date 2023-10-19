package org.knowledgeroot.app.file.domain;

import org.knowledgeroot.app.file.api.filter.FileFilter;
import org.knowledgeroot.app.file.db.File;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileDao {
    List<File> listFiles(FileFilter fileFilter);

    File findById(long id);

    void createFile(MultipartFile file, Integer parentContent);

    void deleteFileById(long id);

    void deleteAllFiles();
}
