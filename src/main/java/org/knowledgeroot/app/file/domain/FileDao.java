package org.knowledgeroot.app.file.domain;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

public interface FileDao {
    List<File> listFiles(FileFilter fileFilter);

    File findById(long id);

    /** Stores the upload and its metadata; returns the id of the new file row. */
    int createFile(MultipartFile file, Integer parentContent, Integer actor);

    void deleteFileById(long id);

    void deleteAllFiles();

    InputStream loadFile(Integer fileId);
}
