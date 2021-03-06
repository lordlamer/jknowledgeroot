package org.knowledgeroot.app.file.impl.database;

import javax.persistence.*;
import java.time.LocalDateTime;

import static javax.persistence.GenerationType.IDENTITY;

@Entity
@Table(name = "file")
public class FileEntity {
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
    private Integer changeDate;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getParent() {
        return parent;
    }

    public void setParent(Integer parent) {
        this.parent = parent;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getDownloads() {
        return downloads;
    }

    public void setDownloads(Integer downloads) {
        this.downloads = downloads;
    }

    public LocalDateTime getTimeStart() {
        return timeStart;
    }

    public void setTimeStart(LocalDateTime timeStart) {
        this.timeStart = timeStart;
    }

    public LocalDateTime getTimeEnd() {
        return timeEnd;
    }

    public void setTimeEnd(LocalDateTime timeEnd) {
        this.timeEnd = timeEnd;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreateDate() {
        return createDate;
    }

    public void setCreateDate(LocalDateTime createDate) {
        this.createDate = createDate;
    }

    public Integer getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(Integer changedBy) {
        this.changedBy = changedBy;
    }

    public Integer getChangeDate() {
        return changeDate;
    }

    public void setChangeDate(Integer changeDate) {
        this.changeDate = changeDate;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}
