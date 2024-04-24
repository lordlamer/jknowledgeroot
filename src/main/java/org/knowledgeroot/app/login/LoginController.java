package org.knowledgeroot.app.login;

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
            Model model
    ) {
        if (error != null) {
            model.addAttribute("error", true);
        } else {
            model.addAttribute("error", false);
        }

        if (logout != null) {
            model.addAttribute("msg", "You've been logged out successfully.");
        }

        return "login";
    }

    @GetMapping("/login/success")
    public String loginSuccess(
            HttpServletResponse response
    ) {
        response.addHeader("HX-Redirect", "/");
        return "login";
    }

    @GetMapping("/logout/success")
    public String logoutSuccess(
            HttpServletResponse response
    ) {
        response.addHeader("HX-Redirect", "/");
        return "login";
    }
}
