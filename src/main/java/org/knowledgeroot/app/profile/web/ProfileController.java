package org.knowledgeroot.app.profile.web;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.profile.domain.MyProfile;
import org.knowledgeroot.app.profile.domain.MyProfileDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Profile controller
 */
@Controller
@Slf4j
@RequiredArgsConstructor
class ProfileController {
    private final MyProfileDao myProfileDao;
    private final UserContext userContext;

    /**
     * Show profile page
     *
     * @param updated flag to show updated message
     * @param model model
     * @return profile page
     */
    @GetMapping("/profile")
    public String showProfile(
            @RequestParam(name = "updated", required = false) boolean updated,
            Model model,
            HtmxRequest htmxRequest
    ) {
        UserDetails userDetails = userContext.getUserContext();

        if(userDetails.isGuest()) {
            return "redirect:/login";
        }

        model.addAttribute("userDetails", myProfileDao.findMyProfile());
        model.addAttribute("updated", updated);

        if (htmxRequest.isHtmxRequest()) {
            return "profile/show :: body";
        }

        return "profile/show";
    }

    /**
     * Update profile and redirect to profile page
     *
     * @param updateProfileDto update profile dto
     * @return redirect to profile page
     */
    @PostMapping("/profile")
    public String updateProfile(@ModelAttribute UpdateProfileDto updateProfileDto) {
        log.info("Update profile: {}", updateProfileDto);

        UserDetails userDetails = userContext.getUserContext();

        if(userDetails.isGuest()) {
            log.info("Guest user tried to update profile");
            return "redirect:/login";
        }

        MyProfile updateProfile = MyProfile.builder()
                .login(updateProfileDto.getLogin())
                .email(updateProfileDto.getEmail())
                .firstName(updateProfileDto.getFirstName())
                .surName(updateProfileDto.getSurName())
                .build();

        myProfileDao.updateMyProfile(updateProfile);

        return "redirect:/profile?updated=true";
    }
}
