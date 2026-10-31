package org.knowledgeroot.app.space;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.user.domain.GroupDao;
import org.knowledgeroot.app.security.user.domain.GroupId;
import org.knowledgeroot.app.security.user.domain.UserDao;
import org.knowledgeroot.app.security.user.domain.UserId;
import org.knowledgeroot.app.util.RequestValidation;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Business rules around spaces: who may create them, who may enter or manage one, and the
 * home page that opens when a viewer switches to a space.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpaceService {
    private final SpaceRepository spaces;
    private final PageDao pages;
    private final PagePermissionDao pagePermissions;
    private final UserContext users;
    private final UserDao userDao;
    private final GroupDao groupDao;

    // --- identity helpers ------------------------------------------------------------------------

    private UserDetails user() {
        return users.getUserContext();
    }

    private static Integer id(UserDetails user) {
        return user.isGuest() ? null : Integer.valueOf(user.getUserId());
    }

    // --- visibility and rights ------------------------------------------------------------------

    public List<Space> visibleSpaces() {
        return spaces.listVisible(id(user()));
    }

    public SpaceRepository spaces() {
        return spaces;
    }

    public Space defaultSpace() {
        return spaces.defaultSpace();
    }

    public Space space(int spaceId) {
        return spaces.find(spaceId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found."));
    }

    /** The viewer's effective level on a space: administrators have every level, archived spaces drop to view. */
    public Optional<SpacePermission.Level> level(int spaceId, UserDetails viewer) {
        if (viewer != null && viewer.isAdmin()) return Optional.of(SpacePermission.Level.ADMIN);
        return spaces.level(spaceId, viewer == null ? null : id(viewer));
    }

    public boolean canView(int spaceId) {
        UserDetails viewer = user();
        if (viewer.isAdmin()) return true;
        var level = level(spaceId, viewer);
        if (level.isEmpty()) return false;
        return !spaces.isArchived(spaceId) || level.get() == SpacePermission.Level.ADMIN;
    }

    /** Root pages need the edit level; nothing can be created in an archived space. */
    public boolean canCreateRootPage(int spaceId) {
        if (spaces.isArchived(spaceId)) return false;
        return level(spaceId, user()).map(level -> level.covers(SpacePermission.Level.EDIT)).orElse(false);
    }

    public boolean canManage(int spaceId) {
        return level(spaceId, user()).map(level -> level == SpacePermission.Level.ADMIN).orElse(false);
    }

    public void requireView(int spaceId) {
        if (!canView(spaceId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this space.");
    }

    public void requireManage(int spaceId) {
        if (!canManage(spaceId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only space administrators can change this space.");
    }

    public boolean canCreateSpaces() {
        UserDetails viewer = user();
        return viewer.isAdmin() || spaces.hasGlobal(SpaceRepository.CREATE_SPACE, id(viewer));
    }

    // --- creation and settings --------------------------------------------------------------------

    /** Creates a space with its home page; the creator becomes its administrator. */
    @Transactional
    public Space create(String key, String name, String description) {
        if (!canCreateSpaces()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You may not create spaces.");
        String normalizedKey = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        if (!Space.KEY.matcher(normalizedKey).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The key needs 2–16 upper-case letters or digits and starts with a letter.");
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty() || trimmedName.length() > 255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a name of at most 255 characters.");
        String trimmedDescription = description == null ? "" : description.trim();
        if (trimmedDescription.length() > 2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The description is limited to 2000 characters.");
        Integer actor = id(user());
        int spaceId;
        try {
            spaceId = spaces.create(Space.builder().key(normalizedKey).name(trimmedName).description(trimmedDescription).createdBy(actor).build());
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A space with this key already exists.");
        }
        if (actor != null) spaces.createPermission(spaceId, SpacePermission.Role.USER, actor, SpacePermission.Level.ADMIN, actor);
        LocalDateTime now = LocalDateTime.now();
        int home = pages.createPage(Page.builder().spaceId(spaceId).parent(0).name(trimmedName)
                .content("<p>Welcome to " + org.springframework.web.util.HtmlUtils.htmlEscape(trimmedName) + ".</p>")
                .active(true).deleted(false).createdBy(actor).createDate(now).changedBy(actor).changeDate(now).build());
        pagePermissions.createDefaultPermissions(new PageId(home), actor);
        Space space = space(spaceId);
        space.setHomePageId(home);
        space.setChangedBy(actor);
        spaces.update(space);
        log.info("Space {} created by user {}", normalizedKey, actor);
        return space(spaceId);
    }

    @Transactional
    public Space update(int spaceId, String name, String description, Integer homePageId) {
        requireManage(spaceId);
        Space space = space(spaceId);
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty() || trimmedName.length() > 255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a name of at most 255 characters.");
        String trimmedDescription = description == null ? "" : description.trim();
        if (trimmedDescription.length() > 2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The description is limited to 2000 characters.");
        if (homePageId != null) {
            RequestValidation.require(homePageId > 0);
            boolean inSpace = spaces.spaceOfPage(homePageId).map(owner -> owner == spaceId).orElse(false)
                    && !Boolean.TRUE.equals(pages.findById(new PageId(homePageId)).getDeleted());
            if (!inSpace) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The home page must be a page of this space.");
        }
        space.setName(trimmedName);
        space.setDescription(trimmedDescription);
        space.setHomePageId(homePageId);
        space.setChangedBy(id(user()));
        spaces.update(space);
        return space;
    }

    /** Archived spaces are read-only and hidden from everyone but administrators and space administrators. */
    @Transactional
    public Space archive(int spaceId, boolean archived) {
        requireManage(spaceId);
        Space space = space(spaceId);
        if (archived && spaces.listAll().stream().filter(s -> !s.isArchived()).count() <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last active space cannot be archived.");
        space.setArchived(archived);
        space.setChangedBy(id(user()));
        spaces.update(space);
        log.info("Space {} {} by user {}", space.getKey(), archived ? "archived" : "restored", id(user()));
        return space;
    }

    // --- permissions ------------------------------------------------------------------------------

    public List<SpacePermission> permissions(int spaceId) {
        requireManage(spaceId);
        return spaces.listPermissions(spaceId);
    }

    @Transactional
    public void addPermission(int spaceId, String roleType, Integer roleId, String level) {
        requireManage(spaceId);
        SpacePermission.Role role;
        SpacePermission.Level permissionLevel;
        try {
            role = SpacePermission.Role.fromValue(roleType);
            permissionLevel = SpacePermission.Level.fromValue(level);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a role and a permission level.");
        }
        Integer id = role.needsId() ? roleId : null;
        if (role.needsId() && (id == null || id <= 0)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a user or group.");
        if (role == SpacePermission.Role.USER && userDao.findById(new UserId(id)) == null
                || role == SpacePermission.Role.GROUP && groupDao.findById(new GroupId(id)) == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected user or group does not exist.");
        if (!role.needsId() && permissionLevel == SpacePermission.Level.ADMIN)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only users and groups can administer a space.");
        if (spaces.hasGrant(spaceId, role, id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "This role already has a permission on the space.");
        spaces.createPermission(spaceId, role, id, permissionLevel, id(user()));
    }

    @Transactional
    public void changePermission(int spaceId, int permissionId, String level) {
        requireManage(spaceId);
        SpacePermission.Level permissionLevel;
        try {
            permissionLevel = SpacePermission.Level.fromValue(level);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a permission level.");
        }
        var grant = spaces.listPermissions(spaceId).stream().filter(p -> p.getId() == permissionId).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found."));
        if (!grant.getRole().needsId() && permissionLevel == SpacePermission.Level.ADMIN)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only users and groups can administer a space.");
        spaces.updatePermission(spaceId, permissionId, permissionLevel, id(user()));
    }

    @Transactional
    public void removePermission(int spaceId, int permissionId) {
        requireManage(spaceId);
        if (!spaces.deletePermission(spaceId, permissionId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found.");
    }

    // --- global grants ------------------------------------------------------------------------------

    public List<SpaceRepository.GlobalGrant> spaceCreators() {
        return spaces.listGlobal(SpaceRepository.CREATE_SPACE);
    }

    public void allowSpaceCreation(String roleType, Integer roleId) {
        SpacePermission.Role role;
        try {
            role = SpacePermission.Role.fromValue(roleType);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a role.");
        }
        if (role == SpacePermission.Role.GUEST) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Guests cannot create spaces.");
        Integer id = role.needsId() ? roleId : null;
        if (role.needsId() && (id == null || id <= 0)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a user or group.");
        if (role == SpacePermission.Role.USER && userDao.findById(new UserId(id)) == null
                || role == SpacePermission.Role.GROUP && groupDao.findById(new GroupId(id)) == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected user or group does not exist.");
        if (!spaces.grantGlobal(SpaceRepository.CREATE_SPACE, role, id, id(user())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This role may already create spaces.");
    }

    public void revokeSpaceCreation(int grantId) {
        if (!spaces.revokeGlobal(grantId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grant not found.");
    }
}
