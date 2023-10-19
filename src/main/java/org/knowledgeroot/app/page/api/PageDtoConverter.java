package org.knowledgeroot.app.page.api;

import org.knowledgeroot.app.page.db.Page;
import org.knowledgeroot.app.util.Converter;

public class PageDtoConverter implements Converter<Page, PageDto> {

    @Override
    public PageDto convertAtoB(Page from) {
        PageDto pageDto = new PageDto();

        pageDto.setId(from.getId());
        pageDto.setActive(from.getActive());
        pageDto.setName(from.getName());
        pageDto.setParent(from.getParent());
        pageDto.setSubtitle(from.getSubtitle());
        pageDto.setCreateDate(from.getCreateDate());
        pageDto.setCreatedBy(from.getCreatedBy());
        pageDto.setChangeDate(from.getChangeDate());
        pageDto.setChangedBy(from.getChangedBy());
        pageDto.setDeleted(from.getDeleted());
        pageDto.setDescription(from.getDescription());
        pageDto.setShowContentDescription(from.getShowContentDescription());
        pageDto.setContentCollapse(from.getContentCollapse());
        pageDto.setContentPosition(from.getContentPosition());
        pageDto.setIcon(from.getIcon());
        pageDto.setShowTableOfContent(from.getShowTableOfContent());
        pageDto.setTooltip(from.getTooltip());
        pageDto.setTimeStart(from.getTimeStart());
        pageDto.setTimeEnd(from.getTimeEnd());
        pageDto.setSorting(from.getSorting());
        pageDto.setAlias(from.getAlias());

        return pageDto;
    }

    @Override
    public Page convertBtoA(PageDto from) {
        Page page = new Page();

        page.setId(from.getId());
        page.setActive(from.getActive());
        page.setName(from.getName());
        page.setParent(from.getParent());
        page.setSubtitle(from.getSubtitle());
        page.setCreateDate(from.getCreateDate());
        page.setCreatedBy(from.getCreatedBy());
        page.setChangeDate(from.getChangeDate());
        page.setChangedBy(from.getChangedBy());
        page.setDeleted(from.getDeleted());
        page.setDescription(from.getDescription());
        page.setShowContentDescription(from.getShowContentDescription());
        page.setContentCollapse(from.getContentCollapse());
        page.setContentPosition(from.getContentPosition());
        page.setIcon(from.getIcon());
        page.setShowTableOfContent(from.getShowTableOfContent());
        page.setTooltip(from.getTooltip());
        page.setTimeStart(from.getTimeStart());
        page.setTimeEnd(from.getTimeEnd());
        page.setSorting(from.getSorting());
        page.setAlias(from.getAlias());

        return page;
    }
}
