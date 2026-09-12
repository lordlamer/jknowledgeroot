package org.knowledgeroot.app.page.domain;

import org.knowledgeroot.app.page.api.PageDto;
import org.knowledgeroot.app.util.RequestValidation;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class PageInput {
    private PageInput() {}
    static void validate(PageDto dto) {
        RequestValidation.require(dto.getName() != null && !dto.getName().isBlank());
        RequestValidation.text(dto.getName(), 255);
        if (dto.getContent() == null) dto.setContent("");
        RequestValidation.require(dto.getContent().getBytes(StandardCharsets.UTF_8).length <= 65_535);
        RequestValidation.range(dto.getTimeStart(), dto.getTimeEnd());
    }
    static void labels(List<String> labels) {
        if (labels == null) return;
        RequestValidation.require(labels.size() <= 32);
        labels.forEach(label -> RequestValidation.text(label == null ? null : label.trim(), 64));
    }
}
