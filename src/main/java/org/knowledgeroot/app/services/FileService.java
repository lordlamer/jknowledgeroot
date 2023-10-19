package org.knowledgeroot.app.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.api.filter.FileFilter;
import org.knowledgeroot.persistence.File.File;
import org.knowledgeroot.domain.FileDao;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class FileService {
    private final FileDao fileImpl;

    public List<File> listFiles(FileFilter fileFilter) {
        return null;
    }

    public File findById(long id) {
        return null;
    }

    public void createFile(MultipartFile file, long parentContent) {

    }

    public void deleteFileById(long id) {

    }

    public void deleteAllFiles() {

    }
}
