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
        return Page.builder()
                .pageId(new PageId(from.getId()))
                .active(from.getActive())
                .name(from.getName())
                .parent(from.getParent())
                .subtitle(from.getSubtitle())
                .createDate(from.getCreateDate())
                .createdBy(from.getCreatedBy())
                .changeDate(from.getChangeDate())
                .changedBy(from.getChangedBy())
                .deleted(from.getDeleted())
                .description(from.getDescription())
                .showContentDescription(from.getShowContentDescription())
                .contentCollapse(from.getContentCollapse())
                .contentPosition(from.getContentPosition())
                .icon(from.getIcon())
                .showTableOfContent(from.getShowTableOfContent())
                .tooltip(from.getTooltip())
                .timeStart(from.getTimeStart())
                .timeEnd(from.getTimeEnd())
                .sorting(from.getSorting())
                .alias(from.getAlias())
                .build();
    }
}
