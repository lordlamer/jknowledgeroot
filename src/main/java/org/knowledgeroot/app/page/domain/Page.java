package org.knowledgeroot.app.page.domain;

import lombok.*;
import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.sanitizer.Sanitizer;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@ToString
public class Page {
    private PageId pageId;
    private Integer parent;
    private String name;
    private String content;
    private LocalDateTime timeStart;
    private LocalDateTime timeEnd;
    private Boolean active;
    private Integer createdBy;
    private LocalDateTime  createDate;
    private Integer changedBy;
    private LocalDateTime changeDate;
    private Boolean deleted;
    private List<File> files;

    public void setContent(String content) {
        this.content = Sanitizer.sanitize(content);
    }

    public static class PageBuilder {
        public PageBuilder content(String content) {
            this.content = Sanitizer.sanitize(content);
            return this;
        }
    }
}
