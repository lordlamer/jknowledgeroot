package org.knowledgeroot.app.page.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PageDto {
    private Integer id;
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

    @JsonFormat
            (shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timeStart;
    @JsonFormat
            (shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timeEnd;
    private Boolean active;
    private Integer createdBy;
    @JsonFormat
            (shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime  createDate;
    private Integer changedBy;
    @JsonFormat
            (shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime changeDate;
    private Boolean deleted;
}
