package org.knowledgeroot.app.page.mapper;

import ma.glasnost.orika.CustomMapper;
import ma.glasnost.orika.MappingContext;
import org.knowledgeroot.app.page.Page;
import org.knowledgeroot.app.page.controller.PageDto;
import org.springframework.stereotype.Component;

@Component
public class PageToPageDtoMapper  extends CustomMapper<Page, PageDto> {
    @Override
    public void mapAtoB(Page page, PageDto pageDto, MappingContext context) {
        super.mapAtoB(page, pageDto, context);
    }

    @Override
    public void mapBtoA(PageDto pageDto, Page page, MappingContext context) {
        super.mapBtoA(pageDto, page, context);
    }
}
