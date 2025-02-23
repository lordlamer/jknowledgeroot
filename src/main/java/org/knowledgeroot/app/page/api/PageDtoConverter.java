package org.knowledgeroot.app.page.api;

import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.util.Converter;

public class PageDtoConverter implements Converter<Page, PageDto> {

    @Override
    public PageDto convertAtoB(Page from) {
        PageDto pageDto = new PageDto();

        pageDto.setId(from.getPageId().value());
        pageDto.setActive(from.getActive());
        pageDto.setName(from.getName());
        pageDto.setParent(from.getParent());
        pageDto.setContent(from.getContent());
        pageDto.setCreateDate(from.getCreateDate());
        pageDto.setCreatedBy(from.getCreatedBy());
        pageDto.setChangeDate(from.getChangeDate());
        pageDto.setChangedBy(from.getChangedBy());
        pageDto.setDeleted(from.getDeleted());
        pageDto.setTimeStart(from.getTimeStart());
        pageDto.setTimeEnd(from.getTimeEnd());

        return pageDto;
    }

    @Override
    public Page convertBtoA(PageDto from) {
        return Page.builder()
                .pageId(from.getId() == null ? null : new PageId(from.getId()))
                .active(from.getActive())
                .name(from.getName())
                .parent(from.getParent())
                .content(from.getContent())
                .createDate(from.getCreateDate())
                .createdBy(from.getCreatedBy())
                .changeDate(from.getChangeDate())
                .changedBy(from.getChangedBy())
                .deleted(from.getDeleted())
                .timeStart(from.getTimeStart())
                .timeEnd(from.getTimeEnd())
                .build();
    }
}
