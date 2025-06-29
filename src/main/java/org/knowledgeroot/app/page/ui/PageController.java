package org.knowledgeroot.app.page.ui;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.api.PageDto;
import org.knowledgeroot.app.page.api.PageDtoConverter;
import org.knowledgeroot.app.page.domain.*;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.user.api.filter.GroupFilter;
import org.knowledgeroot.app.security.user.api.filter.UserFilter;
import org.knowledgeroot.app.security.user.domain.GroupDao;
import org.knowledgeroot.app.security.user.domain.UserDao;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
@RequiredArgsConstructor
public class PageController {
    private final PageDao pageImpl;
    private final PagePermissionDao pagePermissionImpl;
    private final UserDao userImpl;
    private final GroupDao groupImpl;
    private final UserContext userContext;
    private final PageDtoConverter pageDtoConverter = new PageDtoConverter();

    /**
     * Get the current user ID for permission checks.
     * Returns null for guest users, which will be handled by the permission system.
     */
    private Integer getCurrentUserId() {
        UserDetails currentUser = userContext.getUserContext();
        if (currentUser.isGuest()) {
            return null; // Guest users have no user ID
        }
        try {
            return Integer.valueOf(currentUser.getUserId());
        } catch (NumberFormatException e) {
            // If userId is not a valid integer, treat as guest
            return null;
        }
    }

    @GetMapping("/ui/page/new")
    public String showNewPage(HtmxRequest htmxRequest) {
        if(htmxRequest.isHtmxRequest()) {
            return "page/new :: body";
        }

        return "page/new";
    }

    @GetMapping("/ui/page/{pageId}")
    public String showPage(
            @PathVariable("pageId") Integer pageId,
            @RequestParam(name = "trigger", required = false) String trigger,
            Model model,
            HttpServletResponse response,
            HtmxRequest htmxRequest
    ) {
        PageId pid = new PageId(pageId);
        Integer currentUserId = getCurrentUserId();

        // Berechtigungsprüfung für das Anzeigen der Seite
        if (!pagePermissionImpl.hasUserPermission(pid, currentUserId, PagePermission.PermissionLevel.VIEW)) {
            // Keine Berechtigung - umleiten zur Startseite
            return "redirect:/";
        }

        Page page = pageImpl.findById(pid);
        List<Page> hierarchy = pageImpl.getPageHierarchy(pid);

        model.addAttribute("page", page);
        model.addAttribute("breadcrumb", hierarchy);

        // Prüfen der Bearbeitungsberechtigungen
        boolean canEdit = pagePermissionImpl.hasUserPermission(pid, currentUserId, PagePermission.PermissionLevel.EDIT);
        model.addAttribute("canEdit", canEdit);

        // trigger reload sidebar
        if(trigger != null && trigger.equals("reload-sidebar"))
            response.addHeader("HX-Trigger", "reload-sidebar");

        if(htmxRequest.isHtmxRequest()) {
            return "page/show :: body";
        }

        return "page/show";
    }

    @GetMapping("/ui/page/{pageId}/edit")
    public String showEditPage(
            @PathVariable("pageId") Integer pageId,
            @RequestParam(name = "trigger", required = false) String trigger,
            Model model,
            HttpServletResponse response,
            HtmxRequest htmxRequest
    ) {
        // Berechtigungsprüfung
        PageId pid = new PageId(pageId);
        Integer currentUserId = getCurrentUserId();
        if (!pagePermissionImpl.hasUserPermission(pid, currentUserId, PagePermission.PermissionLevel.EDIT)) {
            // Keine Berechtigung - umleiten oder Fehlermeldung anzeigen
            return "redirect:/ui/page/" + pageId;
        }

        Page page = pageImpl.findById(pid);

        // Berechtigungen laden
        List<PagePermission> permissions = pagePermissionImpl.listPermissionsByPageId(pid);

        model.addAttribute("page", page);
        model.addAttribute("permissions", permissions);

        // trigger reload sidebar
        if(trigger != null && trigger.equals("reload-sidebar"))
            response.addHeader("HX-Trigger", "reload-sidebar");

        if(htmxRequest.isHtmxRequest()) {
            return "page/edit :: body";
        }

        return "page/edit";
    }

    @GetMapping("/ui/page/{pageId}/new")
    public String showNewPage(
            @PathVariable("pageId") Integer pageId,
            @RequestParam(name = "trigger", required = false) String trigger,
            Model model,
            HttpServletResponse response,
            HtmxRequest htmxRequest
    ) {
        // Berechtigungsprüfung
        PageId pid = new PageId(pageId);
        Integer currentUserId = getCurrentUserId();
        if (!pagePermissionImpl.hasUserPermission(pid, currentUserId, PagePermission.PermissionLevel.EDIT)) {
            // Keine Berechtigung - umleiten oder Fehlermeldung anzeigen
            return "redirect:/ui/page/" + pageId;
        }

        model.addAttribute("page", pageImpl.findById(pid));

        // trigger reload sidebar
        if(trigger != null && trigger.equals("reload-sidebar"))
            response.addHeader("HX-Trigger", "reload-sidebar");

        if(htmxRequest.isHtmxRequest()) {
            return "page/new :: body";
        }

        return "page/new";
    }

    @PostMapping("/ui/page/{pageId}/edit")
    public ModelAndView editPage(
            @PathVariable("pageId") Integer pageId,
            @ModelAttribute PageDto pageDto,
            @RequestParam MultiValueMap<String, String> allParams
    ) {
        // Parse permission parameters manually from form data
        Map<String, String> permissionUpdates = parsePermissionUpdates(allParams);
        List<String> permissionDeletions = parsePermissionDeletions(allParams);
        List<Map<String, String>> permissionAdditions = parsePermissionAdditions(allParams);
        // Berechtigungsprüfung
        PageId pid = new PageId(pageId);
        Integer currentUserId = getCurrentUserId();
        if (!pagePermissionImpl.hasUserPermission(pid, currentUserId, PagePermission.PermissionLevel.EDIT)) {
            // Keine Berechtigung - umleiten oder Fehlermeldung anzeigen
            return new ModelAndView("redirect:/ui/page/" + pageId);
        }

        pageDto.setId(pageId);
        pageDto.setChangeDate(LocalDateTime.now());
        pageDto.setChangedBy(currentUserId);
        pageDto.setActive(true);
        pageDto.setContent(pageDto.getContent());
        pageDto.setTimeStart(LocalDateTime.now());
        pageDto.setTimeEnd(LocalDateTime.now());
        pageDto.setDeleted(false);
        pageDto.setParent(null);

        Page page = pageDtoConverter.convertBtoA(pageDto);
        pageImpl.updatePage(page);

        // Process permission changes
        LocalDateTime now = LocalDateTime.now();

        // Handle permission updates
        if (permissionUpdates != null && !permissionUpdates.isEmpty()) {
            for (Map.Entry<String, String> entry : permissionUpdates.entrySet()) {
                Integer permissionId = Integer.valueOf(entry.getKey());
                String newLevel = entry.getValue();

                PagePermission permission = PagePermission.builder()
                        .id(permissionId)
                        .permissionLevel(PagePermission.PermissionLevel.fromString(newLevel))
                        .changedBy(currentUserId)
                        .changeDate(now)
                        .build();

                pagePermissionImpl.updatePermission(permission);
            }
        }

        // Handle permission deletions
        if (permissionDeletions != null && !permissionDeletions.isEmpty()) {
            for (String permissionIdStr : permissionDeletions) {
                // Validate permission ID string
                if (permissionIdStr == null || permissionIdStr.trim().isEmpty() || 
                    "null".equals(permissionIdStr) || "undefined".equals(permissionIdStr)) {
                    System.err.println("Invalid permission ID for deletion: " + permissionIdStr);
                    continue; // Skip invalid permission IDs
                }

                try {
                    Integer permissionId = Integer.valueOf(permissionIdStr.trim());
                    pagePermissionImpl.deletePermission(permissionId);
                } catch (NumberFormatException e) {
                    System.err.println("Failed to parse permission ID for deletion: " + permissionIdStr + " - " + e.getMessage());
                    // Continue with other deletions instead of failing the entire operation
                }
            }
        }

        // Handle permission additions
        if (permissionAdditions != null && !permissionAdditions.isEmpty()) {
            for (Map<String, String> additionData : permissionAdditions) {
                String roleType = additionData.get("roleType");
                String roleIdStr = additionData.get("roleId");
                String permissionLevel = additionData.get("permissionLevel");

                Integer roleId = null;
                if (roleIdStr != null && !roleIdStr.isEmpty() && !"null".equals(roleIdStr)) {
                    roleId = Integer.valueOf(roleIdStr);
                }

                PagePermission permission = PagePermission.builder()
                        .pageId(pid)
                        .roleType(PagePermission.RoleType.fromString(roleType))
                        .roleId(roleId)
                        .permissionLevel(PagePermission.PermissionLevel.fromString(permissionLevel))
                        .createdBy(currentUserId)
                        .createDate(now)
                        .changedBy(currentUserId)
                        .changeDate(now)
                        .build();

                pagePermissionImpl.createPermission(permission);
            }
        }

        return new ModelAndView("redirect:/ui/page/" + page.getPageId().value() + "?trigger=reload-sidebar");
    }

    @PostMapping("/ui/page/new")
    public ModelAndView createNewPage(@ModelAttribute PageDto pageDto) {
        Integer currentUserId = getCurrentUserId();

        // Bei neuer Unterseite Berechtigungsprüfung für Parent
        if (pageDto.getParent() != null && pageDto.getParent() > 0) {
            PageId parentId = new PageId(pageDto.getParent());
            if (!pagePermissionImpl.hasUserPermission(parentId, currentUserId, PagePermission.PermissionLevel.EDIT)) {
                // Keine Berechtigung - umleiten
                return new ModelAndView("redirect:/");
            }
        }

        pageDto.setCreateDate(LocalDateTime.now());
        pageDto.setCreatedBy(currentUserId);
        pageDto.setChangeDate(LocalDateTime.now());
        pageDto.setChangedBy(currentUserId);
        pageDto.setActive(true);
        pageDto.setContent(pageDto.getContent());
        pageDto.setTimeStart(LocalDateTime.now());
        pageDto.setTimeEnd(LocalDateTime.now());
        pageDto.setDeleted(false);

        if(pageDto.getParent() == null)
            pageDto.setParent(0);

        Page page = pageDtoConverter.convertBtoA(pageDto);
        pageImpl.createPage(page);

        // Standardberechtigungen erstellen
        pagePermissionImpl.createDefaultPermissions(page.getPageId(), page.getCreatedBy());

        if(page.getParent() != null && page.getParent() > 0)
            return new ModelAndView("redirect:/ui/page/" + page.getParent() + "?trigger=reload-sidebar");
        else
            return new ModelAndView("redirect:/?trigger=reload-sidebar");
    }

    @DeleteMapping("/ui/page/{pageId}")
    public ModelAndView deletePage(@PathVariable("pageId") Integer pageId) {
        // Berechtigungsprüfung
        PageId pid = new PageId(pageId);
        Integer currentUserId = getCurrentUserId();
        if (!pagePermissionImpl.hasUserPermission(pid, currentUserId, PagePermission.PermissionLevel.EDIT)) {
            // Keine Berechtigung - umleiten
            return new ModelAndView("redirect:/ui/page/" + pageId);
        }

        pageImpl.deletePageById(pid);

        return new ModelAndView("redirect:/ui/welcome?trigger=reload-sidebar");
    }

    // Berechtigungsverwaltung

    @GetMapping("/ui/page/{pageId}/permissions")
    public String showPagePermissions(
            @PathVariable("pageId") Integer pageId,
            Model model,
            HtmxRequest htmxRequest
    ) {
        // Berechtigungsprüfung
        PageId pid = new PageId(pageId);
        Integer currentUserId = getCurrentUserId();
        if (!pagePermissionImpl.hasUserPermission(pid, currentUserId, PagePermission.PermissionLevel.EDIT)) {
            // Keine Berechtigung - umleiten
            return "redirect:/ui/page/" + pageId;
        }

        Page page = pageImpl.findById(pid);
        List<PagePermission> permissions = pagePermissionImpl.listPermissionsByPageId(pid);

        model.addAttribute("page", page);
        model.addAttribute("permissions", permissions);

        if(htmxRequest.isHtmxRequest()) {
            return "page/permissions :: body";
        }

        return "page/permissions";
    }

    @PostMapping("/ui/page/{pageId}/permission")
    public String addPermission(
            @PathVariable("pageId") Integer pageId,
            @RequestParam("roleType") String roleType,
            @RequestParam(value = "roleId", required = false) Integer roleId,
            @RequestParam("permissionLevel") String permissionLevel,
            Model model
    ) {
        // Berechtigungsprüfung
        PageId pid = new PageId(pageId);
        Integer currentUserId = getCurrentUserId();
        if (!pagePermissionImpl.hasUserPermission(pid, currentUserId, PagePermission.PermissionLevel.EDIT)) {
            // Keine Berechtigung - umleiten
            return "redirect:/ui/page/" + pageId;
        }

        LocalDateTime now = LocalDateTime.now();

        PagePermission permission = PagePermission.builder()
                .pageId(pid)
                .roleType(PagePermission.RoleType.fromString(roleType))
                .roleId(roleId)
                .permissionLevel(PagePermission.PermissionLevel.fromString(permissionLevel))
                .createdBy(currentUserId)
                .createDate(now)
                .changedBy(currentUserId)
                .changeDate(now)
                .build();

        pagePermissionImpl.createPermission(permission);

        // Berechtigungen neu laden
        List<PagePermission> permissions = pagePermissionImpl.listPermissionsByPageId(pid);
        model.addAttribute("permissions", permissions);
        model.addAttribute("page", pageImpl.findById(pid));

        return "page/edit :: #permissions-content";
    }

    @PutMapping("/ui/page/{pageId}/permission/{permissionId}")
    public ResponseEntity<String> updatePermission(
            @PathVariable("pageId") Integer pageId,
            @PathVariable("permissionId") Integer permissionId,
            @RequestParam("permissionLevel") String permissionLevel
    ) {
        // Berechtigungsprüfung
        PageId pid = new PageId(pageId);
        Integer currentUserId = getCurrentUserId();
        if (!pagePermissionImpl.hasUserPermission(pid, currentUserId, PagePermission.PermissionLevel.EDIT)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Keine Berechtigung");
        }

        PagePermission permission = PagePermission.builder()
                .id(permissionId)
                .permissionLevel(PagePermission.PermissionLevel.fromString(permissionLevel))
                .changedBy(currentUserId)
                .changeDate(LocalDateTime.now())
                .build();

        pagePermissionImpl.updatePermission(permission);

        return ResponseEntity.ok("Berechtigung aktualisiert");
    }

    @DeleteMapping("/ui/page/{pageId}/permission/{permissionId}")
    public String deletePermission(
            @PathVariable("pageId") Integer pageId,
            @PathVariable("permissionId") Integer permissionId,
            Model model
    ) {
        // Berechtigungsprüfung
        PageId pid = new PageId(pageId);
        Integer currentUserId = getCurrentUserId();
        if (!pagePermissionImpl.hasUserPermission(pid, currentUserId, PagePermission.PermissionLevel.EDIT)) {
            return "redirect:/ui/page/" + pageId;
        }

        pagePermissionImpl.deletePermission(permissionId);

        // Berechtigungen neu laden
        List<PagePermission> permissions = pagePermissionImpl.listPermissionsByPageId(pid);
        model.addAttribute("permissions", permissions);
        model.addAttribute("page", pageImpl.findById(pid));

        return "page/edit :: #permissions-content";
    }

    // REST-API-Endpunkte für Benutzer- und Gruppeninformationen

    @GetMapping("/api/users")
    @ResponseBody
    public ResponseEntity<?> getUsers() {
        try {
            // Create filter for active and non-deleted users
            UserFilter filter = new UserFilter();
            filter.setActive(true);
            filter.setDeleted(false);

            var users = userImpl.listUsers(filter);
            var userList = users.stream()
                .map(user -> Map.of(
                    "id", user.getId().value(),
                    "firstName", user.getFirstName(),
                    "lastName", user.getLastName()
                ))
                .toList();
            return ResponseEntity.ok(userList);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error retrieving users: " + e.getMessage());
        }
    }

    @GetMapping("/api/groups")
    @ResponseBody
    public ResponseEntity<?> getGroups() {
        try {
            // Create filter for active and non-deleted groups
            GroupFilter filter = new GroupFilter();
            filter.setActive(true);
            filter.setDeleted(false);

            var groups = groupImpl.listGroups(filter);
            var groupList = groups.stream()
                .map(group -> Map.of(
                    "id", group.getId().value(),
                    "name", group.getName()
                ))
                .toList();
            return ResponseEntity.ok(groupList);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error retrieving groups: " + e.getMessage());
        }
    }

    // Helper methods to parse permission parameters from form data
    private Map<String, String> parsePermissionUpdates(MultiValueMap<String, String> allParams) {
        Map<String, String> updates = new HashMap<>();
        Pattern pattern = Pattern.compile("permissionUpdates\\[(\\d+)\\]");

        for (String key : allParams.keySet()) {
            Matcher matcher = pattern.matcher(key);
            if (matcher.matches()) {
                String permissionId = matcher.group(1);
                String value = allParams.getFirst(key);
                if (value != null) {
                    updates.put(permissionId, value);
                }
            }
        }
        return updates;
    }

    private List<String> parsePermissionDeletions(MultiValueMap<String, String> allParams) {
        List<String> deletions = new ArrayList<>();
        List<String> values = allParams.get("permissionDeletions[]");
        if (values != null) {
            deletions.addAll(values);
        }
        return deletions;
    }

    private List<Map<String, String>> parsePermissionAdditions(MultiValueMap<String, String> allParams) {
        List<Map<String, String>> additions = new ArrayList<>();
        Map<Integer, Map<String, String>> additionsByIndex = new HashMap<>();

        Pattern pattern = Pattern.compile("permissionAdditions\\[(\\d+)\\]\\[(\\w+)\\]");

        for (String key : allParams.keySet()) {
            Matcher matcher = pattern.matcher(key);
            if (matcher.matches()) {
                int index = Integer.parseInt(matcher.group(1));
                String field = matcher.group(2);
                String value = allParams.getFirst(key);

                additionsByIndex.computeIfAbsent(index, k -> new HashMap<>()).put(field, value);
            }
        }

        // Convert to list in order
        for (int i = 0; i < additionsByIndex.size(); i++) {
            if (additionsByIndex.containsKey(i)) {
                additions.add(additionsByIndex.get(i));
            }
        }

        return additions;
    }
}
