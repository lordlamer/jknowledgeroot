package org.knowledgeroot.app.content.mapper;

import ma.glasnost.orika.CustomMapper;
import ma.glasnost.orika.MappingContext;
import org.knowledgeroot.app.content.Content;
import org.knowledgeroot.app.content.controller.ContentDto;
import org.springframework.stereotype.Component;

@Component
public class ContentToContentDtoMapper extends CustomMapper<Content, ContentDto> {
    @Override
    public void mapAtoB(Content content, ContentDto contentDto, MappingContext context) {
        super.mapAtoB(content, contentDto, context);
    }

    @Override
    public void mapBtoA(ContentDto contentDto, Content content, MappingContext context) {
        super.mapBtoA(contentDto, content, context);
    }
}
