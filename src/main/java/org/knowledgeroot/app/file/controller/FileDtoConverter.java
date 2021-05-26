package org.knowledgeroot.app.file.controller;

import org.knowledgeroot.app.file.impl.database.File;
import org.knowledgeroot.app.util.Converter;

public class FileDtoConverter implements Converter<File, FileDto> {

    @Override
    public FileDto convertAtoB(File from) {
        FileDto fileDto = new FileDto();

        fileDto.setId(from.getId());
        fileDto.setActive(from.getActive());
        fileDto.setName(from.getName());
        fileDto.setParent(from.getParent());
        fileDto.setType(from.getType());
        fileDto.setSize(from.getSize());
        fileDto.setHash(from.getHash());
        fileDto.setCreateDate(from.getCreateDate());
        fileDto.setCreatedBy(from.getCreatedBy());
        fileDto.setChangedBy(from.getChangedBy());
        fileDto.setChangeDate(from.getCreateDate());
        fileDto.setDeleted(from.getDeleted());
        fileDto.setDownloads(from.getDownloads());
        fileDto.setTimeStart(from.getTimeStart());
        fileDto.setTimeEnd(from.getTimeEnd());

        return fileDto;
    }

    @Override
    public File convertBtoA(FileDto from) {
        File file = new File();

        file.setId(from.getId());
        file.setActive(from.getActive());
        file.setName(from.getName());
        file.setParent(from.getParent());
        file.setType(from.getType());
        file.setSize(from.getSize());
        file.setHash(from.getHash());
        file.setCreateDate(from.getCreateDate());
        file.setCreatedBy(from.getCreatedBy());
        file.setChangedBy(from.getChangedBy());
        file.setChangeDate(from.getChangeDate());
        file.setDeleted(from.getDeleted());
        file.setDownloads(from.getDownloads());
        file.setTimeStart(from.getTimeStart());
        file.setTimeEnd(from.getTimeEnd());

        return file;
    }
}
