package org.knowledgeroot.app.frontend.web;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
class InfoController {
    @RequestMapping("/help")
    public String showHelp(HtmxRequest htmxRequest) {
        if(htmxRequest.isHtmxRequest()) {
            return "info/help :: body";
        }

        return "info/help";
    }

    @RequestMapping("/about")
    public String showAbout(HtmxRequest htmxRequest) {
        if (htmxRequest.isHtmxRequest()) {
            return "info/about :: body";
        }

        return "info/about";
    }
}
