package org.knowledgeroot.app.profile.ui;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.profile.domain.MyProfile;
import org.knowledgeroot.app.profile.domain.MyProfileDao;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Slf4j
@RequiredArgsConstructor
class ProfileController {
    private final MyProfileDao myProfileDao;

    @GetMapping("/profile")
    public String showProfile(
            @RequestParam(name = "updated", required = false) boolean updated,
            Model model
    ) {
        model.addAttribute("userDetails", myProfileDao.findMyProfile());
        model.addAttribute("updated", updated);

        return "profile/show";
    }

    @PostMapping("/profile")
    public String updateProfile(@ModelAttribute UpdateProfileDto updateProfileDto) {
        log.info("Update profile: {}", updateProfileDto);

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
