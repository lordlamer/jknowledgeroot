package org.knowledgeroot.app.user;

import java.time.LocalDateTime;

public class User {
    private Integer id;
    private String firstName;
    private String lastName;
    private String login;
    private String email;
    private String password;
    private String language;
    private String timezone;

    private LocalDateTime timeStart;
    private LocalDateTime timeEnd;
    private Boolean active;
    private Integer createdBy;
    private LocalDateTime  createDate;
    private Integer changedBy;
    private Boolean deleted;
}
