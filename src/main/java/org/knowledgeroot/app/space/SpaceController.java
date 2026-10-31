package org.knowledgeroot.app.space;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageFilter;
import org.knowledgeroot.app.security.user.api.filter.GroupFilter;
import org.knowledgeroot.app.security.user.api.filter.UserFilter;
import org.knowledgeroot.app.security.user.domain.GroupDao;
import org.knowledgeroot.app.security.user.domain.UserDao;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Space overview, creation and the settings page (name, home page, permissions, archiving). */
@Controller
@RequiredArgsConstructor
class SpaceController {
    private final SpaceService spaces;
    private final PageDao pages;
    private final UserDao users;
    private final GroupDao groups;
    private final CurrentSpace currentSpace;
    private final org.knowledgeroot.app.security.context.domain.UserContext userContext;
    private final org.knowledgeroot.app.frontend.web.LayoutDefines layout;

    @GetMapping("/spaces")
    String list(Model model, HtmxRequest htmx) {
        model.addAttribute("spaces", spaces.visibleSpaces());
        model.addAttribute("canCreate", spaces.canCreateSpaces());
        return htmx.isHtmxRequest() ? "space/list :: body" : "space/list";
    }

    /** Switches the sidebar to a space and opens its home page, or the overview when it has none. */
    @GetMapping("/spaces/open")
    String open(@RequestParam int space) {
        spaces.requireView(space);
        currentSpace.switchTo(space);
        Space target = spaces.space(space);
        if (target.getHomePageId() != null) return "redirect:/ui/page/" + target.getHomePageId() + "?trigger=reload-sidebar";
        return "redirect:/spaces/" + space + "?trigger=reload-sidebar";
    }

    @GetMapping("/spaces/{id}")
    String overview(@PathVariable int id, @RequestParam(required = false) String trigger, Model model, HtmxRequest htmx,
                    jakarta.servlet.http.HttpServletResponse response) {
        spaces.requireView(id);
        boolean spaceChanged = currentSpace.switchTo(id);
        if (spaceChanged) layout.spaceChanged(model, response, htmx.isHtmxRequest());
        Space space = spaces.space(id);
        var roots = new PageFilter();
        roots.setSpaceId(id);
        roots.setParent(0);
        roots.setDeleted(false);
        roots.setLimit(101);
        var viewer = userContextViewer();
        model.addAttribute("space", space);
        model.addAttribute("rootPages", pages.listVisiblePages(roots, viewer));
        model.addAttribute("canCreate", spaces.canCreateRootPage(id));
        model.addAttribute("canManage", spaces.canManage(id));
        if (!spaceChanged && "reload-sidebar".equals(trigger)) response.addHeader("HX-Trigger", "reload-sidebar");
        return htmx.isHtmxRequest() ? "space/overview :: body" : "space/overview";
    }

    private Integer userContextViewer() {
        var user = userContext.getUserContext();
        return user.isGuest() ? null : Integer.valueOf(user.getUserId());
    }

    @GetMapping("/spaces/new")
    String create(Model model, HtmxRequest htmx) {
        if (!spaces.canCreateSpaces()) throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        return htmx.isHtmxRequest() ? "space/new :: body" : "space/new";
    }

    @PostMapping("/spaces")
    String create(@RequestParam String key, @RequestParam String name, @RequestParam(defaultValue = "") String description,
                  Model model, HtmxRequest htmx) {
        try {
            Space space = spaces.create(key, name, description);
            return "redirect:/spaces/" + space.getId() + "/settings?created";
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() != 400 && e.getStatusCode().value() != 409) throw e;
            model.addAttribute("error", e.getReason());
            model.addAttribute("key", key);
            model.addAttribute("name", name);
            model.addAttribute("description", description);
            return htmx.isHtmxRequest() ? "space/new :: body" : "space/new";
        }
    }

    @GetMapping("/spaces/{id}/settings")
    String settings(@PathVariable int id, @RequestParam(required = false) String created,
                    @RequestParam(required = false) String saved, Model model, HtmxRequest htmx) {
        spaces.requireManage(id);
        settingsModel(id, model);
        model.addAttribute("created", created != null);
        model.addAttribute("saved", saved != null);
        return htmx.isHtmxRequest() ? "space/settings :: body" : "space/settings";
    }

    @PostMapping("/spaces/{id}/settings")
    String save(@PathVariable int id, @RequestParam String name, @RequestParam(defaultValue = "") String description,
                @RequestParam(required = false) Integer homePageId, Model model, HtmxRequest htmx) {
        try {
            spaces.update(id, name, description, homePageId == null || homePageId == 0 ? null : homePageId);
            return "redirect:/spaces/" + id + "/settings?saved";
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() != 400) throw e;
            settingsModel(id, model);
            model.addAttribute("error", e.getReason());
            return htmx.isHtmxRequest() ? "space/settings :: body" : "space/settings";
        }
    }

    @PostMapping("/spaces/{id}/archive")
    String archive(@PathVariable int id, @RequestParam boolean archived, Model model, HtmxRequest htmx) {
        try {
            spaces.archive(id, archived);
            return "redirect:/spaces/" + id + "/settings?saved";
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() != 409) throw e;
            settingsModel(id, model);
            model.addAttribute("error", e.getReason());
            return htmx.isHtmxRequest() ? "space/settings :: body" : "space/settings";
        }
    }

    @PostMapping("/spaces/{id}/permissions")
    String addPermission(@PathVariable int id, @RequestParam String roleType, @RequestParam(required = false) Integer roleId,
                         @RequestParam String level, Model model, HtmxRequest htmx) {
        try {
            spaces.addPermission(id, roleType, roleId, level);
            return "redirect:/spaces/" + id + "/settings?saved";
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() != 400 && e.getStatusCode().value() != 409) throw e;
            settingsModel(id, model);
            model.addAttribute("error", e.getReason());
            return htmx.isHtmxRequest() ? "space/settings :: body" : "space/settings";
        }
    }

    @PostMapping("/spaces/{id}/permissions/{permissionId}")
    String changePermission(@PathVariable int id, @PathVariable int permissionId, @RequestParam String level) {
        spaces.changePermission(id, permissionId, level);
        return "redirect:/spaces/" + id + "/settings?saved";
    }

    @PostMapping("/spaces/{id}/permissions/{permissionId}/delete")
    String removePermission(@PathVariable int id, @PathVariable int permissionId) {
        spaces.removePermission(id, permissionId);
        return "redirect:/spaces/" + id + "/settings?saved";
    }

    private void settingsModel(int id, Model model) {
        Space space = spaces.space(id);
        model.addAttribute("space", space);
        model.addAttribute("permissions", spaces.permissions(id));
        model.addAttribute("levels", SpacePermission.Level.values());
        var rootFilter = new PageFilter();
        rootFilter.setParent(0);
        rootFilter.setDeleted(false);
        model.addAttribute("rootPages", pages.listPages(rootFilter).stream()
                .filter(page -> java.util.Objects.equals(page.getSpaceId(), id)).toList());
        var activeUsers = new UserFilter();
        activeUsers.setActive(true);
        activeUsers.setDeleted(false);
        model.addAttribute("users", users.listUsers(activeUsers));
        var activeGroups = new GroupFilter();
        activeGroups.setActive(true);
        activeGroups.setDeleted(false);
        model.addAttribute("groups", groups.listGroups(activeGroups));
    }
}
