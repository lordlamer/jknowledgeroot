package org.knowledgeroot.app.ui;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
public class IndexController {
    @RequestMapping("/")
    public String index(Model model) {
        return "knowledgeroot";
    }

    @RequestMapping("/ui/welcome")
    public String welcome(
        @RequestParam(name = "trigger", required = false) String trigger,
        Model model,
        HttpServletResponse response
    ) {
        // trigger reload sidebar
        if(trigger != null && trigger.equals("reload-sidebar"))
            response.addHeader("HX-Trigger", "reload-sidebar");

        return "welcome";
    }
}
