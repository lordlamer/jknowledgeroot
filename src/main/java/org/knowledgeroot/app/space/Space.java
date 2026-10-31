package org.knowledgeroot.app.space;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/** A container around one page tree with its own permissions and an optional home page. */
@Getter
@Setter
@Builder
public class Space {
    /** Two to sixteen upper-case letters and digits, starting with a letter, like Confluence space keys. */
    public static final Pattern KEY = Pattern.compile("[A-Z][A-Z0-9]{1,15}");
    private Integer id;
    private String key;
    private String name;
    private String description;
    private Integer homePageId;
    private boolean archived;
    private Integer createdBy;
    private LocalDateTime createDate;
    private Integer changedBy;
    private LocalDateTime changeDate;
}
