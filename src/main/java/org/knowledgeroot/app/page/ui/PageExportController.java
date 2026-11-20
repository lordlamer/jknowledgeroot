package org.knowledgeroot.app.page.ui;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.export.ExportFormat;
import org.knowledgeroot.app.page.export.PageExportService;
import org.knowledgeroot.app.util.RequestValidation;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Download of one page as PDF, Word or Markdown (R69); the service applies the view permission. */
@Controller
@RequiredArgsConstructor
class PageExportController {
    private final PageExportService exports;

    @GetMapping("/ui/page/{pageId}/export")
    public ResponseEntity<byte[]> export(@PathVariable("pageId") Integer pageId,
                                         @RequestParam(name = "format", required = false) String format) {
        RequestValidation.id(pageId);
        return exports.export(pageId, ExportFormat.parse(format)).response();
    }
}
