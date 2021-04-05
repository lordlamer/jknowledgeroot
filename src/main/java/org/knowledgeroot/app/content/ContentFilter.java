package org.knowledgeroot.app.content;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentFilter {
    private Integer id;
    private Integer parent;
    private String name;
    private String content;
    private String type;
    private Integer sorting;

    private LocalDateTime timeStartBegin;
    private LocalDateTime timeStartEnd;
    private LocalDateTime timeEndBegin;
    private LocalDateTime timeEndEnd;
    private Boolean active;
    private Integer createdBy;
    private LocalDateTime  createDateBegin;
    private LocalDateTime  createDateEnd;
    private Integer changedBy;
    private LocalDateTime changeDateBegin;
    private LocalDateTime changeDateEnd;
    private Boolean deleted;

    private Integer start;
    private Integer limit;
}
