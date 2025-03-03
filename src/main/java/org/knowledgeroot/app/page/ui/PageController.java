package org.knowledgeroot.app.page.ui;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.api.PageDto;
import org.knowledgeroot.app.page.api.PageDtoConverter;
import org.knowledgeroot.app.page.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class PageController {
    private final PageDao pageImpl;
    private final PagePermissionDao pagePermissionImpl;
    private final PageDtoConverter pageDtoConverter = new PageDtoConverter();

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
        Page page = pageImpl.findById(pid);
        List<Page> hierarchy = pageImpl.getPageHierarchy(pid);

        model.addAttribute("page", page);
        model.addAttribute("breadcrumb", hierarchy);

        // Prüfen der Berechtigungen
        // In einem realen System würden Sie den aktuellen Benutzer aus der Session holen
        Integer currentUserId = 1; // Demo-Benutzer-ID
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
        Integer currentUserId = 1; // Demo-Benutzer-ID
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
        Integer currentUserId = 1; // Demo-Benutzer-ID
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
            @ModelAttribute PageDto pageDto
    ) {
        // Berechtigungsprüfung
        PageId pid = new PageId(pageId);
        Integer currentUserId = 1; // Demo-Benutzer-ID
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

        return new ModelAndView("redirect:/ui/page/" + page.getPageId().value() + "?trigger=reload-sidebar");
    }

    @PostMapping("/ui/page/new")
    public ModelAndView createNewPage(@ModelAttribute PageDto pageDto) {
        Integer currentUserId = 1; // Demo-Benutzer-ID

        // Bei neuer Unterseite Berechtigungsprüfung für Parent
        if (pageDto.getParent() != null && pageDto.getParent() > 0) {
            PageId parentId = new PageId(pageDto.getParent());
            if (!pagePermissionImpl.hasUserPermission(parentId, currentUserId, PagePermission.PermissionLevel.EDIT)) {
                // Keine Berechtigung - umleiten
                return new ModelAndView("redirect:/ui/welcome");
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
        Integer currentUserId = 1; // Demo-Benutzer-ID
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
        Integer currentUserId = 1; // Demo-Benutzer-ID
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
        Integer currentUserId = 1; // Demo-Benutzer-ID
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
        Integer currentUserId = 1; // Demo-Benutzer-ID
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
        Integer currentUserId = 1; // Demo-Benutzer-ID
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
        // Hier würden Sie eine Methode aufrufen, die alle Benutzer zurückgibt
        // Beispiel-Implementierung (in der Praxis würden Sie eine Benutzer-DAO verwenden)
        return ResponseEntity.ok("[{\"id\":1,\"firstName\":\"Admin\",\"lastName\":\"User\"},{\"id\":2,\"firstName\":\"Test\",\"lastName\":\"User\"}]");
    }

    @GetMapping("/api/groups")
    @ResponseBody
    public ResponseEntity<?> getGroups() {
        // Hier würden Sie eine Methode aufrufen, die alle Gruppen zurückgibt
        // Beispiel-Implementierung (in der Praxis würden Sie eine Gruppen-DAO verwenden)
        return ResponseEntity.ok("[{\"id\":1,\"name\":\"Administrators\"},{\"id\":2,\"name\":\"Editors\"}]");
    }
}