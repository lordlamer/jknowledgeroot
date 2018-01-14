package org.knowledgeroot.app.content;

import java.time.LocalDateTime;

public class ContentFilter {
    private Integer id;
    private Integer parent;
    private String name;
    private String content;
    private String type;
    private Integer sorting;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getSorting() {
        return sorting;
    }

    public void setSorting(Integer sorting) {
        this.sorting = sorting;
    }

    public LocalDateTime getTimeStartBegin() {
        return timeStartBegin;
    }

    public void setTimeStartBegin(LocalDateTime timeStartBegin) {
        this.timeStartBegin = timeStartBegin;
    }

    public LocalDateTime getTimeStartEnd() {
        return timeStartEnd;
    }

    public void setTimeStartEnd(LocalDateTime timeStartEnd) {
        this.timeStartEnd = timeStartEnd;
    }

    public LocalDateTime getTimeEndBegin() {
        return timeEndBegin;
    }

    public void setTimeEndBegin(LocalDateTime timeEndBegin) {
        this.timeEndBegin = timeEndBegin;
    }

    public LocalDateTime getTimeEndEnd() {
        return timeEndEnd;
    }

    public void setTimeEndEnd(LocalDateTime timeEndEnd) {
        this.timeEndEnd = timeEndEnd;
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

    public LocalDateTime getCreateDateBegin() {
        return createDateBegin;
    }

    public void setCreateDateBegin(LocalDateTime createDateBegin) {
        this.createDateBegin = createDateBegin;
    }

    public LocalDateTime getCreateDateEnd() {
        return createDateEnd;
    }

    public void setCreateDateEnd(LocalDateTime createDateEnd) {
        this.createDateEnd = createDateEnd;
    }

    public Integer getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(Integer changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getChangeDateBegin() {
        return changeDateBegin;
    }

    public void setChangeDateBegin(LocalDateTime changeDateBegin) {
        this.changeDateBegin = changeDateBegin;
    }

    public LocalDateTime getChangeDateEnd() {
        return changeDateEnd;
    }

    public void setChangeDateEnd(LocalDateTime changeDateEnd) {
        this.changeDateEnd = changeDateEnd;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public Integer getStart() {
        return start;
    }

    public void setStart(Integer start) {
        this.start = start;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
