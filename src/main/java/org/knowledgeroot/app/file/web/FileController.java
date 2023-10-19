package org.knowledgeroot.app.file.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class FileController {
    @GetMapping("/file/new")
    public String showNewFile(Model model) {
        return "file/new";
    }
}
