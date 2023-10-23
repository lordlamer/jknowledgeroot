package org.knowledgeroot.app.file.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
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
