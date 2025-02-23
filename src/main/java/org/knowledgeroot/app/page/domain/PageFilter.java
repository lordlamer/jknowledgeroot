package org.knowledgeroot.app.page.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageFilter {
    private Integer id;
    private Integer parent;
    private String name;
    private String content;

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
