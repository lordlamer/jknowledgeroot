package org.knowledgeroot.app.file.mapper;

import ma.glasnost.orika.CustomMapper;
import ma.glasnost.orika.MappingContext;
import org.knowledgeroot.app.file.File;
import org.knowledgeroot.app.file.impl.database.FileEntity;
import org.springframework.stereotype.Component;

@Component
public class FileToFileEntityMapper extends CustomMapper<File, FileEntity> {
    @Override
    public void mapAtoB(File file, FileEntity fileEntity, MappingContext context) {
        super.mapAtoB(file, fileEntity, context);
    }

    @Override
    public void mapBtoA(FileEntity fileEntity, File file, MappingContext context) {
        super.mapBtoA(fileEntity, file, context);
    }
}
