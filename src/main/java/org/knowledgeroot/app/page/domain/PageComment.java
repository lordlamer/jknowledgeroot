package org.knowledgeroot.app.page.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PageComment {
    private Integer id;
    private PageId pageId;
    private Integer userId;
    private String content;
    private LocalDateTime createDate;

    // Denormalised author info (filled via JOIN on read; null on write)
    private String authorLogin;
    private String authorFirstName;
    private String authorLastName;

    public String getAuthorDisplayName() {
        if (authorFirstName != null && authorLastName != null
                && (!authorFirstName.isBlank() || !authorLastName.isBlank())) {
            return (authorFirstName + " " + authorLastName).trim();
        }
        return authorLogin != null ? authorLogin : "User";
    }

    public String getAuthorInitial() {
        String name = getAuthorDisplayName();
        return name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
    }
}
