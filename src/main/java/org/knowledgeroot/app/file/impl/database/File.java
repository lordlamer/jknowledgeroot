package org.knowledgeroot.app.file.impl.database;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

import static javax.persistence.GenerationType.IDENTITY;

@Entity
@Table(name = "file")
@Data
public class File {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

    @Column(name = "parent", nullable = false)
    private Integer parent;

    @Column(name = "hash", nullable = false)
    private String hash;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "size", nullable = false)
    private Integer size;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "downloads", nullable = false)
    private Integer downloads;


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