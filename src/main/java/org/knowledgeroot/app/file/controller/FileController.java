package org.knowledgeroot.app.file.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FileController {
    @GetMapping("/file/new")
    public String showNewFile(Model model) {
        return "file/new";
    }
}
