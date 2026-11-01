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
    private Integer spaceId;
    private Integer parent;
    private String name;
    private String content;
    private String query;

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

    /** Only pages carrying this label (exact name). */
    private String label;
    /** Newest change first instead of the id order. */
    private Boolean newestFirst;

    private Integer start;
    private Integer limit;
}
