package org.knowledgeroot.app.content.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.knowledgeroot.app.sanitizer.Sanitizer;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class Content {
    private ContentId contentId;

    @NonNull
    private Integer parent;

    private String name;
    private String content;
    private String type;
    private Integer sorting;
    private LocalDateTime timeStart;
    private LocalDateTime timeEnd;
    private Boolean active;
    private Integer createdBy;
    private LocalDateTime  createDate;
    private Integer changedBy;
    private LocalDateTime changeDate;
    private Boolean deleted;

    public void setContent(String content) {
        this.content = Sanitizer.sanitize(content);
    }

    public static class ContentBuilder {
        public ContentBuilder content(String content) {
            this.content = Sanitizer.sanitize(content);
            return this;
        }
    }
}

