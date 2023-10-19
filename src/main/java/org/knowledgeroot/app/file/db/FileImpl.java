package org.knowledgeroot.app.file.db;

import org.knowledgeroot.app.file.api.filter.FileFilter;
import org.knowledgeroot.app.file.domain.FileDao;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class FileImpl implements FileDao {
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
