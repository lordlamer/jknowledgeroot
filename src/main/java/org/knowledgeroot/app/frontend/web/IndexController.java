package org.knowledgeroot.app.frontend.web;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;


@Controller
class IndexController {

    @RequestMapping("/")
    public String index(HtmxRequest htmxRequest) {
        if(htmxRequest.isHtmxRequest()) {
            return "welcome :: body";
        }

        return "welcome";
    }
}
