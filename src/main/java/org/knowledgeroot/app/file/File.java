package org.knowledgeroot.app.file;

import java.time.LocalDateTime;

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
    private Boolean deleted;
}
