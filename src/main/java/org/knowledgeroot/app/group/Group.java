package org.knowledgeroot.app.group;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Group {
    private Integer id;
    private String name;
    private String description;

    private LocalDateTime timeStart;
    private LocalDateTime timeEnd;
    private Boolean active;
    private Integer createdBy;
    private LocalDateTime  createDate;
    private Integer changedBy;
    private Boolean deleted;
}
