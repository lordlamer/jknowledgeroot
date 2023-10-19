package org.knowledgeroot.persistence.User;

import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import static jakarta.persistence.GenerationType.IDENTITY;

@Entity
@Table(name = "user")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "login", nullable = false)
    private String login;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "language", nullable = false)
    private String language;

    @Column(name = "timezone", nullable = false)
    private String timezone;


    @Column(name = "time_start")
    private LocalDateTime timeStart;

    @Column(name = "time_end")
    private LocalDateTime timeEnd;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_by", nullable = false)
    private Integer createdBy;

    @Column(name = "create_date", nullable = false)
    private LocalDateTime  createDate;

    @Column(name = "changed_by", nullable = false)
    private Integer changedBy;

    @Column(name = "change_date", nullable = false)
    private LocalDateTime changeDate;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted;
}
