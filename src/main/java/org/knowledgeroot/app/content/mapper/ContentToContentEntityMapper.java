package org.knowledgeroot.app.content.mapper;

import ma.glasnost.orika.CustomMapper;
import ma.glasnost.orika.MappingContext;
import org.knowledgeroot.app.content.Content;
import org.knowledgeroot.app.content.impl.database.ContentEntity;
import org.springframework.stereotype.Component;

@Component
public class ContentToContentEntityMapper extends CustomMapper<Content, ContentEntity> {
    @Override
    public void mapAtoB(Content content, ContentEntity contentEntity, MappingContext context) {
        super.mapAtoB(content, contentEntity, context);
    }

    @Override
    public void mapBtoA(ContentEntity contentEntity, Content content, MappingContext context) {
        super.mapBtoA(contentEntity, content, context);
    }
}
