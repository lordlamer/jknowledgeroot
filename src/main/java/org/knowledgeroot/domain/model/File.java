package org.knowledgeroot.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class File {
    private Integer id;
    private Integer parent;
    private String hash;
    private String name;
    private Integer size;
    private String type;
    private Integer downloads;
    private LocalDateTime timeStart;
    private LocalDateTime timeEnd;
    private Boolean active;
    private Integer createdBy;
    private LocalDateTime  createDate;
    private Integer changedBy;
    private LocalDateTime changeDate;
    private Boolean deleted;
}
