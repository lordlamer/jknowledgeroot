package org.knowledgeroot.app.file.domain;

import lombok.*;
import org.knowledgeroot.app.page.domain.PageId;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileFilter {
    private PageId pageId;
}
