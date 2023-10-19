package org.knowledgeroot.app.api.filter;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserFilter {
    private Integer id;
    private String firstName;
    private String lastName;
    private String login;
    private String email;
    private String language;
    private String timezone;

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
