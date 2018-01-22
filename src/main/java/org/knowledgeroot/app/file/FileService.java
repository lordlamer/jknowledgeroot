package org.knowledgeroot.app.file;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class FileService {
    @Autowired
    private FileDao fileImpl;

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
