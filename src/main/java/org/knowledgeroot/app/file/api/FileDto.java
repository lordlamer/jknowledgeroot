package org.knowledgeroot.app.file.api;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDto {
    private Integer id;
    private Integer pageId;
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
