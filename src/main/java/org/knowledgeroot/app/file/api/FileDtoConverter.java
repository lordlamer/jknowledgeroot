package org.knowledgeroot.app.file.api;

import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.util.Converter;

public class FileDtoConverter implements Converter<File, FileDto> {

    @Override
    public FileDto convertAtoB(File from) {
        FileDto fileDto = new FileDto();

        fileDto.setId(from.getId());
        fileDto.setName(from.getName());
        fileDto.setPageId(from.getPageId());
        fileDto.setType(from.getType());
        fileDto.setSize(from.getSize());
        fileDto.setHash(from.getHash());
        fileDto.setCreateDate(from.getCreateDate());
        fileDto.setCreatedBy(from.getCreatedBy());
        fileDto.setChangedBy(from.getChangedBy());
        fileDto.setChangeDate(from.getCreateDate());
        fileDto.setDeleted(from.getDeleted());
        fileDto.setDownloads(from.getDownloads());

        return fileDto;
    }

    @Override
    public File convertBtoA(FileDto from) {
        return File.builder()
                .id(from.getId())
                .name(from.getName())
                .pageId(from.getPageId())
                .type(from.getType())
                .size(from.getSize())
                .hash(from.getHash())
                .createDate(from.getCreateDate())
                .createdBy(from.getCreatedBy())
                .changedBy(from.getChangedBy())
                .changeDate(from.getCreateDate())
                .deleted(from.getDeleted())
                .downloads(from.getDownloads())
                .build();
    }
}
