package org.knowledgeroot.app.login;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRedirect;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class LoginController {
    @GetMapping("/login")
    public String login(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            Model model,
            HtmxRequest htmxRequest
    ) {
        if (error != null) {
            model.addAttribute("error", true);
        } else {
            model.addAttribute("error", false);
        }

        if (logout != null) {
            model.addAttribute("msg", "You've been logged out successfully.");
        }

        if(htmxRequest.isHtmxRequest()) {
            return "login :: body";
        }

        return "login";
    }

    @HxRedirect("/")
    @GetMapping("/login/success")
    public String loginSuccess(
            HttpServletResponse response
    ) {
        return "login";
    }

    @HxRedirect("/")
    @GetMapping("/logout/success")
    public String logoutSuccess(
            HttpServletResponse response
    ) {
        return "login";
    }
}
