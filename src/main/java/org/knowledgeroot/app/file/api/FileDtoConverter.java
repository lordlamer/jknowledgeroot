package org.knowledgeroot.app.file.api;

import org.knowledgeroot.app.file.domain.File;
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
        return File.builder()
                .id(from.getId())
                .active(from.getActive())
                .name(from.getName())
                .parent(from.getParent())
                .type(from.getType())
                .size(from.getSize())
                .hash(from.getHash())
                .createDate(from.getCreateDate())
                .createdBy(from.getCreatedBy())
                .changedBy(from.getChangedBy())
                .changeDate(from.getCreateDate())
                .deleted(from.getDeleted())
                .downloads(from.getDownloads())
                .timeStart(from.getTimeStart())
                .timeEnd(from.getTimeEnd())
                .build();
    }
}
