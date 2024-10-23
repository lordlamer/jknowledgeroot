package org.knowledgeroot.app.file.domain;

import lombok.*;
import org.knowledgeroot.app.content.domain.ContentId;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileFilter {
    private ContentId contentId;
}
