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
    @Builder.Default
    private Long revision = 0L;
    /** The space this page belongs to; children always share their parent's space. */
    private Integer spaceId;
    private Integer parent;
    private String name;
    private String content;
    private LocalDateTime timeStart;
    private LocalDateTime timeEnd;
    private Boolean active;
    private Integer createdBy;
    private String createdByLogin;
    private LocalDateTime  createDate;
    private Integer changedBy;
    private String changedByLogin;
    private LocalDateTime changeDate;
    private Boolean deleted;
    /** False for a draft page that was never published; only users who may edit it can see it. */
    @Builder.Default
    private Boolean published = Boolean.TRUE;
    private List<File> files;

    public boolean isPublished() {
        return !Boolean.FALSE.equals(published);
    }

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
