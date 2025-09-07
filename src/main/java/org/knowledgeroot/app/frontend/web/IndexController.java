package org.knowledgeroot.app.frontend.web;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
class IndexController {

    @GetMapping({"/", "/ui/welcome"})
    public String index(
            @RequestParam(name = "trigger", required = false) String trigger,
            HtmxRequest htmxRequest,
            HttpServletResponse response
    ) {
        // trigger reload sidebar
        if(trigger != null && trigger.equals("reload-sidebar"))
            response.addHeader("HX-Trigger", "reload-sidebar");

        if(htmxRequest.isHtmxRequest()) {
            return "welcome :: body";
        }

        return "welcome";
    }
}
