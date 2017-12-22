package org.knowledgeroot.app.group;

import java.time.LocalDateTime;

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
