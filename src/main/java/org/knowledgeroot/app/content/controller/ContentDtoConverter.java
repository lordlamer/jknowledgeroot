package org.knowledgeroot.app.content.controller;

import org.knowledgeroot.app.content.impl.database.Content;
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
        Content content = new Content();

        content.setId(from.getId());
        content.setContent(from.getContent());
        content.setActive(from.getActive());
        content.setParent(from.getParent());
        content.setName(from.getName());
        content.setType(from.getType());
        content.setCreatedBy(from.getCreatedBy());
        content.setCreateDate(from.getCreateDate());
        content.setChangedBy(from.getChangedBy());
        content.setChangeDate(from.getChangeDate());
        content.setDeleted(from.getDeleted());
        content.setSorting(from.getSorting());
        content.setTimeStart(from.getTimeStart());
        content.setTimeEnd(from.getTimeEnd());

        return content;
    }
}
