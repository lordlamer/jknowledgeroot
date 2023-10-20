package org.knowledgeroot.app.content.api;

import org.knowledgeroot.app.content.domain.Content;
import org.knowledgeroot.app.util.Converter;

public class ContentDtoConverter implements Converter<Content, ContentDto> {
    @Override
    public ContentDto convertAtoB(Content from) {
        ContentDto contentDto = new ContentDto();

        contentDto.setId(from.getId());
        contentDto.setContent(from.getContent());
        contentDto.setActive(from.getActive());
        contentDto.setParent(from.getParent());
        contentDto.setName(from.getName());
        contentDto.setType(from.getType());
        contentDto.setCreatedBy(from.getCreatedBy());
        contentDto.setCreateDate(from.getCreateDate());
        contentDto.setChangedBy(from.getChangedBy());
        contentDto.setChangeDate(from.getChangeDate());
        contentDto.setDeleted(from.getDeleted());
        contentDto.setSorting(from.getSorting());
        contentDto.setTimeStart(from.getTimeStart());
        contentDto.setTimeEnd(from.getTimeEnd());

        return contentDto;
    }

    @Override
    public Content convertBtoA(ContentDto from) {
        return Content.builder()
                .id(from.getId())
                .content(from.getContent())
                .active(from.getActive())
                .parent(from.getParent())
                .name(from.getName())
                .type(from.getType())
                .createdBy(from.getCreatedBy())
                .createDate(from.getCreateDate())
                .changedBy(from.getChangedBy())
                .changeDate(from.getChangeDate())
                .deleted(from.getDeleted())
                .sorting(from.getSorting())
                .timeStart(from.getTimeStart())
                .timeEnd(from.getTimeEnd())
                .build();
    }
}
