package org.knowledgeroot.app.content;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Content {
    private Integer id;
    private Integer parent;
    private String name;
    private String content;
    private String type;
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
