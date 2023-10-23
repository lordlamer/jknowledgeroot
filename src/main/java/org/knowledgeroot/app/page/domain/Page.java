package org.knowledgeroot.app.page.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class Page {
    private PageId pageId;
    private Integer parent;
    private String name;
    private String subtitle;
    private String description;
    private String tooltip;
    private String icon;
    private String alias;
    private Boolean contentCollapse;
    private String contentPosition;
    private Boolean showContentDescription;
    private Boolean showTableOfContent;
    private Integer sorting;
    private LocalDateTime timeStart;
    private LocalDateTime timeEnd;
    private Boolean active;
    private Integer createdBy;
    private LocalDateTime  createDate;
    private Integer changedBy;
    private LocalDateTime changeDate;
    private Boolean deleted;
}
