package org.knowledgeroot.app.page.db;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

import static jakarta.persistence.GenerationType.IDENTITY;

@Entity
@Table(name = "page")
@Getter
@Setter
public class Page {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

    @Column(name = "parent", nullable = false)
    private Integer parent;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "subtitle", nullable = false)
    private String subtitle;

    @Column(name = "description")
    private String description;

    @Column(name = "tooltip", nullable = false)
    private String tooltip;

    @Column(name = "icon", nullable = false)
    private String icon;

    @Column(name = "alias", nullable = false)
    private String alias;

    @Column(name = "content_collapse", nullable = false)
    private Boolean contentCollapse;

    @Column(name = "content_position", nullable = false)
    private String contentPosition;

    @Column(name = "show_content_description", nullable = false)
    private Boolean showContentDescription;

    @Column(name = "show_table_of_content", nullable = false)
    private Boolean showTableOfContent;

    @Column(name = "sorting", nullable = false)
    private Integer sorting;


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
