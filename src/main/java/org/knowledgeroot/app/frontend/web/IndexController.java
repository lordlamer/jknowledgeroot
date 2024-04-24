package org.knowledgeroot.app.frontend.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
@RequiredArgsConstructor
class IndexController {
    private final UserContext userContext;

    @RequestMapping("/")
    public String index(Model model) {
        UserDetails userDetails = userContext.getUserContext();

        if(userDetails.isGuest()) {
            model.addAttribute("isguest", true);
        } else {
            model.addAttribute("isguest", false);
        }

        if (userDetails.isAdmin()) {
            model.addAttribute("isadmin", true);
        } else {
            model.addAttribute("isadmin", false);
        }

        if(userDetails.isUser()) {
            model.addAttribute("isuser", true);
        } else {
            model.addAttribute("isuser", false);
        }

        System.out.println("User: " + userDetails.getLogin() + " is guest: " + userDetails.isGuest() + " is admin: " + userDetails.isAdmin() + " is user: " + userDetails.isUser());

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
