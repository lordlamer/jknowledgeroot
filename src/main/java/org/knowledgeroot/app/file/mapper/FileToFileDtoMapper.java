package org.knowledgeroot.app.file.mapper;

import ma.glasnost.orika.CustomMapper;
import ma.glasnost.orika.MappingContext;
import org.knowledgeroot.app.file.File;
import org.knowledgeroot.app.file.controller.FileDto;
import org.springframework.stereotype.Component;

@Component
public class FileToFileDtoMapper extends CustomMapper<File, FileDto> {
    @Override
    public void mapAtoB(File file, FileDto fileDto, MappingContext context) {
        super.mapAtoB(file, fileDto, context);
    }

    @Override
    public void mapBtoA(FileDto fileDto, File file, MappingContext context) {
        super.mapBtoA(fileDto, file, context);
    }
}
