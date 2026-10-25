package org.knowledgeroot.app.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.profile.domain.MyProfile;
import org.knowledgeroot.app.profile.domain.MyProfileDao;
import org.knowledgeroot.app.profile.domain.ProfilePasswordService;
import org.knowledgeroot.app.security.auth.PasswordService;
import org.knowledgeroot.app.security.recovery.AccountRecoveryService;
import org.knowledgeroot.app.security.recovery.RecoveryEmailRepository;
import org.knowledgeroot.app.security.recovery.RecoverySettings;
import org.knowledgeroot.app.security.user.api.filter.GroupFilter;
import org.knowledgeroot.app.security.user.api.filter.UserFilter;
import org.knowledgeroot.app.security.user.domain.*;
import org.knowledgeroot.app.util.RequestValidation;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** The signed-in user's profile and the administrators' user and group management. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class AccountApiController {
    private final ApiSupport api;
    private final MyProfileDao profiles;
    private final ProfilePasswordService profilePasswords;
    private final PasswordService passwords;
    private final AccountRecoveryService recovery;
    private final RecoveryEmailRepository recoveryEmails;
    private final RecoverySettings recoverySettings;
    private final UserDao users;
    private final GroupDao groups;

    @GetMapping("/profile")
    ApiModels.Profile profile() {
        api.requireUser();
        return profile(profiles.findMyProfile());
    }

    @PutMapping("/profile")
    ApiModels.Profile updateProfile(@RequestBody ApiModels.Profile input) {
        api.requireUser();
        String login = required(input.login(), "Enter a login name.");
        String email = optionalEmail(input.email());
        profiles.updateMyProfile(MyProfile.builder().login(login).email(email)
                .firstName(text(input.firstName())).surName(text(input.lastName())).build());
        return profile(profiles.findMyProfile());
    }

    /** Changes the password after re-authentication and ends this session, like the web UI. */
    @PostMapping("/profile/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@RequestBody ApiModels.PasswordChange input, HttpServletRequest request,
                        HttpServletResponse response) {
        api.requireUser();
        profilePasswords.change(input.currentPassword(), input.newPassword(), input.confirmPassword(),
                request.getRemoteAddr());
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }

    @GetMapping("/profile/recovery")
    ApiModels.RecoveryStatus recoveryStatus() {
        int id = api.requireUser();
        if (!recoverySettings.isEnabled()) return new ApiModels.RecoveryStatus(false, api.user().getEmail(), false);
        return new ApiModels.RecoveryStatus(true, api.user().getEmail(), recoveryEmails.verified(id));
    }

    /** Sends a confirmation link to the profile e-mail address. */
    @PostMapping("/profile/recovery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestVerification(@RequestBody ApiModels.CurrentPassword input, HttpServletRequest request) {
        int id = api.requireUser();
        recovery.requestVerification(id, input.currentPassword(), request.getRemoteAddr());
    }

    /** Active users and groups that can receive page permissions. */
    @GetMapping("/admin/principals")
    ApiModels.Principals principals() {
        api.requireAdmin();
        var userFilter = new UserFilter();
        userFilter.setActive(true);
        userFilter.setDeleted(false);
        var groupFilter = new GroupFilter();
        groupFilter.setActive(true);
        groupFilter.setDeleted(false);
        return new ApiModels.Principals(
                users.listUsers(userFilter).stream().map(user -> new ApiModels.Principal(user.getId().value(),
                        displayName(user), user.getLogin())).sorted(Comparator.comparing(ApiModels.Principal::name,
                        String.CASE_INSENSITIVE_ORDER)).toList(),
                groups.listGroups(groupFilter).stream().map(group -> new ApiModels.Principal(group.getId().value(),
                        group.getName(), group.getDescription())).sorted(Comparator.comparing(ApiModels.Principal::name,
                        String.CASE_INSENSITIVE_ORDER)).toList());
    }

    @GetMapping("/admin/users")
    List<ApiModels.AdminUser> listUsers() {
        api.requireAdmin();
        return users.listUsers(new UserFilter()).stream().map(ApiModels.AdminUser::of).toList();
    }

    @GetMapping("/admin/users/{id}")
    ApiModels.AdminUser user(@PathVariable int id) {
        api.requireAdmin();
        return ApiModels.AdminUser.of(existingUser(id));
    }

    @PostMapping("/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    ApiModels.AdminUser createUser(@RequestBody ApiModels.AdminUserInput input) {
        int actor = api.requireUser();
        api.requireAdmin();
        String login = required(input.login(), "Enter a login name.");
        if (users.isUserExist(User.builder().login(login).build()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This login name is already taken.");
        LocalDateTime now = LocalDateTime.now();
        users.createUser(User.builder().login(login).firstName(text(input.firstName())).lastName(text(input.lastName()))
                .email(optionalEmail(input.email())).password(passwords.encodeNewPassword(input.password()))
                .admin(Boolean.TRUE.equals(input.admin())).language("en_US").timezone("UTC")
                .timeStart(now).timeEnd(now).active(!Boolean.FALSE.equals(input.active()))
                .createdBy(actor).createDate(now).changedBy(actor).changeDate(now).deleted(false).build());
        var filter = new UserFilter();
        filter.setLogin(login);
        return users.listUsers(filter).stream().filter(user -> login.equals(user.getLogin())).findFirst()
                .map(ApiModels.AdminUser::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "The user could not be created."));
    }

    /** Updates a user; an empty password keeps the current one. */
    @PutMapping("/admin/users/{id}")
    ApiModels.AdminUser updateUser(@PathVariable int id, @RequestBody ApiModels.AdminUserInput input) {
        int actor = api.requireUser();
        api.requireAdmin();
        User user = existingUser(id);
        String login = required(input.login(), "Enter a login name.");
        if (!login.equals(user.getLogin()) && users.isUserExist(User.builder().login(login).build()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This login name is already taken.");
        boolean admin = Boolean.TRUE.equals(input.admin());
        boolean active = input.active() == null ? Boolean.TRUE.equals(user.getActive()) : input.active();
        if (id == actor && (!admin || !active))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot remove your own administrator access.");
        String replacement = passwords.encodeReplacement(input.password());
        user.setLogin(login);
        user.setFirstName(text(input.firstName()));
        user.setLastName(text(input.lastName()));
        user.setEmail(optionalEmail(input.email()));
        user.setAdmin(admin);
        user.setActive(active);
        if (replacement != null) user.setPassword(replacement);
        user.setChangedBy(actor);
        user.setChangeDate(LocalDateTime.now());
        users.updateUser(user);
        return ApiModels.AdminUser.of(existingUser(id));
    }

    @DeleteMapping("/admin/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteUser(@PathVariable int id) {
        int actor = api.requireUser();
        api.requireAdmin();
        if (id == actor) throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot delete your own account.");
        existingUser(id);
        users.deleteUserById(new UserId(id));
    }

    @GetMapping("/admin/groups")
    List<ApiModels.AdminGroup> listGroups() {
        api.requireAdmin();
        return groups.listGroups(new GroupFilter()).stream().map(ApiModels.AdminGroup::of).toList();
    }

    @PostMapping("/admin/groups")
    @ResponseStatus(HttpStatus.CREATED)
    ApiModels.AdminGroup createGroup(@RequestBody ApiModels.AdminGroupInput input) {
        int actor = api.requireUser();
        api.requireAdmin();
        String name = required(input.name(), "Enter a group name.");
        if (groups.isGroupExist(Group.builder().name(name).build()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A group with this name already exists.");
        LocalDateTime now = LocalDateTime.now();
        groups.createGroup(Group.builder().name(name).description(text(input.description())).active(true)
                .createdBy(actor).createDate(now).changedBy(actor).changeDate(now).deleted(false).build());
        var filter = new GroupFilter();
        filter.setName(name);
        return groups.listGroups(filter).stream().filter(group -> name.equals(group.getName())).findFirst()
                .map(ApiModels.AdminGroup::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "The group could not be created."));
    }

    @PutMapping("/admin/groups/{id}")
    ApiModels.AdminGroup updateGroup(@PathVariable int id, @RequestBody ApiModels.AdminGroupInput input) {
        int actor = api.requireUser();
        api.requireAdmin();
        Group group = existingGroup(id);
        String name = required(input.name(), "Enter a group name.");
        if (!name.equals(group.getName()) && groups.isGroupExist(Group.builder().name(name).build()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A group with this name already exists.");
        group.setName(name);
        group.setDescription(text(input.description()));
        group.setChangedBy(actor);
        group.setChangeDate(LocalDateTime.now());
        groups.updateGroup(group);
        return ApiModels.AdminGroup.of(existingGroup(id));
    }

    @DeleteMapping("/admin/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGroup(@PathVariable int id) {
        api.requireAdmin();
        existingGroup(id);
        groups.deleteGroupById(new GroupId(id));
    }

    private User existingUser(int id) {
        RequestValidation.require(id > 0);
        User user = users.findById(new UserId(id));
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        return user;
    }

    private Group existingGroup(int id) {
        RequestValidation.require(id > 0);
        Group group = groups.findById(new GroupId(id));
        if (group == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found.");
        return group;
    }

    private static ApiModels.Profile profile(MyProfile profile) {
        return new ApiModels.Profile(profile.getLogin(), profile.getEmail(), profile.getFirstName(), profile.getSurName());
    }

    private static String displayName(User user) {
        String name = (text(user.getFirstName()) + " " + text(user.getLastName())).trim();
        return name.isEmpty() ? user.getLogin() : name;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        String trimmed = value.trim();
        if (trimmed.length() > 255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use at most 255 characters.");
        return trimmed;
    }

    private static String text(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.length() > 255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use at most 255 characters.");
        return trimmed;
    }

    private static String optionalEmail(String value) {
        String email = text(value);
        if (!email.isEmpty() && RecoverySettings.email(email) == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid e-mail address.");
        return email;
    }
}
