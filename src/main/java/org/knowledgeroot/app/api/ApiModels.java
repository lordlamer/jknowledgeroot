package org.knowledgeroot.app.api;

import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageComment;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PageRevision;
import org.knowledgeroot.app.page.domain.RevisionDiff;
import org.knowledgeroot.app.security.user.domain.Group;
import org.knowledgeroot.app.security.user.domain.User;

import tools.jackson.databind.annotation.JsonSerialize;

import java.time.LocalDateTime;
import java.util.List;

/** JSON shapes of the client API. Field names are part of the public contract. */
public final class ApiModels {
    private ApiModels() {}

    public record CurrentUser(int id, String login, String firstName, String lastName, String email, boolean admin) {}

    public record Csrf(String headerName, String token) {}

    public record Capabilities(boolean canCreateRootPage, boolean recoveryEnabled, long uploadMaxFileBytes,
                               int uploadMaxFiles) {}

    /** pending: the password was accepted and a code (requirement verify) or an enrolment (enrol) is owed. */
    public record SecondFactor(boolean pending, String requirement, boolean enrolled, String policy) {}

    public record SessionInfo(boolean authenticated, CurrentUser user, Capabilities capabilities, Csrf csrf,
                              String serverVersion, SecondFactor mfa) {}

    public record CodeRequest(String code) {}

    public record SecondFactorStatus(boolean enrolled, @JsonSerialize(using = ServerTime.class) LocalDateTime enrolledAt,
                                     int recoveryCodesLeft, boolean required, String policy) {}

    /** Shown once; {@code qrSvg} is an inline SVG of the otpauth URI, {@code secret} the manual key. */
    public record SecondFactorEnrolment(String secret, String otpauthUri, String qrSvg) {}

    /** {@code authenticated} is true when a pending sign-in was completed by the confirmation. */
    public record SecondFactorConfirmed(List<String> recoveryCodes, boolean authenticated) {}

    public record SecondFactorRemoval(String currentPassword, String code) {}

    public record LoginRequest(String username, String password) {}

    public record EmailRequest(String email) {}

    public record PageRef(int id, String name) {
        static PageRef of(Page page) { return new PageRef(page.getPageId().value(), page.getName()); }
    }

    public record TreeNode(int id, String name, Integer parent, long revision, boolean hasChildren, boolean starred) {}

    public record TreeResponse(PageRef parent, List<TreeNode> items, Integer nextAfter) {}

    public record FileInfo(int id, int pageId, String name, Integer size, String type, Integer downloads,
                           Integer createdBy, @JsonSerialize(using = ServerTime.class) LocalDateTime createDate) {
        static FileInfo of(File file) {
            return new FileInfo(file.getId(), file.getPageId(), file.getName(), file.getSize(), file.getType(),
                    file.getDownloads(), file.getCreatedBy(), file.getCreateDate());
        }
    }

    public record PageDetail(int id, long revision, Integer parent, String name, String content, List<String> labels,
                             List<PageRef> breadcrumb, boolean deleted,
                             Integer createdBy, String createdByLogin, @JsonSerialize(using = ServerTime.class) LocalDateTime createDate,
                             Integer changedBy, String changedByLogin, @JsonSerialize(using = ServerTime.class) LocalDateTime changeDate,
                             boolean canEdit, boolean canStar, boolean starred, boolean canComment,
                             boolean canManagePermissions, List<FileInfo> files, int commentCount) {}

    public record PageInput(Integer parent, String name, String content, List<String> labels, Long revision) {}

    public record Created(int id) {}

    public record Comment(int id, int pageId, Integer userId, String authorName, String authorLogin, String content,
                          @JsonSerialize(using = ServerTime.class) LocalDateTime createDate, boolean canDelete) {
        static Comment of(PageComment comment, Integer viewer, boolean admin) {
            boolean own = comment.getUserId() != null && comment.getUserId().equals(viewer);
            return new Comment(comment.getId(), comment.getPageId().value(), comment.getUserId(),
                    comment.getAuthorDisplayName(), comment.getAuthorLogin(), comment.getContent(),
                    comment.getCreateDate(), viewer != null && (own || admin));
        }
    }

    public record CommentInput(String content) {}

    public record PermissionEntry(int id, String roleType, Integer roleId, String roleName, String level) {
        static PermissionEntry of(PagePermission permission) {
            return new PermissionEntry(permission.getId(), permission.getRoleType().getValue(), permission.getRoleId(),
                    permission.getRoleName(), permission.getPermissionLevel().getValue());
        }
    }

    public record PermissionInfo(int pageId, boolean inherits, PageRef source, boolean hasParent,
                                 List<PermissionEntry> entries) {}

    public record PermissionInput(String roleType, Integer roleId, String level) {}

    public record PermissionModeInput(boolean inherit) {}

    public record Principal(int id, String name, String detail) {}

    public record Principals(List<Principal> users, List<Principal> groups) {}

    public record RevisionSummary(int id, long revision, String name, @JsonSerialize(using = ServerTime.class) LocalDateTime changedAt, boolean labelsCaptured) {
        static RevisionSummary of(PageRevision revision) {
            return new RevisionSummary(revision.id(), revision.revision(), revision.name(), revision.changedAt(),
                    revision.labelsCaptured());
        }
    }

    public record HistoryPage(int id, String name, long revision, boolean deleted, @JsonSerialize(using = ServerTime.class) LocalDateTime changeDate) {
        static HistoryPage of(Page page) {
            return new HistoryPage(page.getPageId().value(), page.getName(), page.getRevision(),
                    Boolean.TRUE.equals(page.getDeleted()), page.getChangeDate());
        }
    }

    public record HistoryResponse(HistoryPage page, List<RevisionSummary> items, int start, boolean hasNext) {}

    public record RevisionDetail(int id, long revision, String name, String content, @JsonSerialize(using = ServerTime.class) LocalDateTime changedAt,
                                 boolean labelsCaptured, List<String> labels, HistoryPage page) {}

    public record DiffPart(String kind, String text) {
        static List<DiffPart> of(List<RevisionDiff.Part> parts) {
            return parts.stream().map(part -> new DiffPart(part.kind(), part.text())).toList();
        }
    }

    public record CompareSide(int id, long revision, String name, @JsonSerialize(using = ServerTime.class) LocalDateTime changedAt, boolean current) {}

    public record CompareResponse(HistoryPage page, CompareSide left, CompareSide right, List<DiffPart> nameDiff,
                                  List<DiffPart> textDiff, List<DiffPart> contentDiff) {}

    public record RevisionInput(Long revision) {}

    public record MoveInput(int parent, Long revision) {}

    public record MoveOptions(PageRef page, int currentParent, List<PageRef> currentPath, int parent,
                              List<PageRef> destinationPath, List<MoveCandidate> items, int start, boolean hasNext,
                              boolean alreadyHere, boolean validDestination, boolean inherits) {}

    public record MoveCandidate(int id, String name) {}

    public record SearchHit(int id, String name, String snippet, List<String> path, @JsonSerialize(using = ServerTime.class) LocalDateTime changeDate) {}

    public record SearchResponse(String query, List<SearchHit> items, int start, boolean hasNext) {}

    public record DeletedPage(int id, String name, @JsonSerialize(using = ServerTime.class) LocalDateTime changeDate, String changedByLogin) {}

    public record DeletedResponse(List<DeletedPage> items, int start, boolean hasNext) {}

    public record Profile(String login, String email, String firstName, String lastName) {}

    public record PasswordChange(String currentPassword, String newPassword, String confirmPassword) {}

    public record RecoveryStatus(boolean enabled, String email, boolean verified) {}

    public record CurrentPassword(String currentPassword) {}

    public record AdminUser(int id, String login, String firstName, String lastName, String email, boolean admin,
                            boolean active, @JsonSerialize(using = ServerTime.class) LocalDateTime createDate, @JsonSerialize(using = ServerTime.class) LocalDateTime changeDate) {
        static AdminUser of(User user) {
            return new AdminUser(user.getId().value(), user.getLogin(), user.getFirstName(), user.getLastName(),
                    user.getEmail(), Boolean.TRUE.equals(user.getAdmin()), Boolean.TRUE.equals(user.getActive()),
                    user.getCreateDate(), user.getChangeDate());
        }
    }

    public record AdminUserInput(String login, String firstName, String lastName, String email, String password,
                                 Boolean admin, Boolean active) {}

    public record AdminGroup(int id, String name, String description, boolean active, @JsonSerialize(using = ServerTime.class) LocalDateTime createDate,
                             @JsonSerialize(using = ServerTime.class) LocalDateTime changeDate) {
        static AdminGroup of(Group group) {
            return new AdminGroup(group.getId().value(), group.getName(), group.getDescription(),
                    Boolean.TRUE.equals(group.getActive()), group.getCreateDate(), group.getChangeDate());
        }
    }

    public record AdminGroupInput(String name, String description) {}
}
