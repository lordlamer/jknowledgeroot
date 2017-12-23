package org.knowledgeroot.app.page.mapper;

import ma.glasnost.orika.CustomMapper;
import ma.glasnost.orika.MappingContext;
import org.knowledgeroot.app.page.Page;
import org.knowledgeroot.app.page.impl.database.PageEntity;
import org.springframework.stereotype.Component;

@Component
public class PageToPageEntityMapper  extends CustomMapper<Page, PageEntity> {
    @Override
    public void mapAtoB(Page page, PageEntity pageEntity, MappingContext context) {
        super.mapAtoB(page, pageEntity, context);
    }

    @Override
    public void mapBtoA(PageEntity pageEntity, Page page, MappingContext context) {
        super.mapBtoA(pageEntity, page, context);
    }
}
