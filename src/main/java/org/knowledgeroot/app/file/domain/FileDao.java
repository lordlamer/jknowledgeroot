package org.knowledgeroot.app.file.domain;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileDao {
    List<File> listFiles(FileFilter fileFilter);

    File findById(long id);

    void createFile(MultipartFile file, Integer parentContent);

    void deleteFileById(long id);

    void deleteAllFiles();
}
