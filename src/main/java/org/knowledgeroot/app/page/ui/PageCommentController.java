package org.knowledgeroot.app.page.ui;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.PageComment;
import org.knowledgeroot.app.page.domain.PageCommentDao;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class PageCommentController {
    private static final int MAX_COMMENT_LENGTH = 4000;

    private final PageCommentDao pageCommentDao;
    private final PagePermissionDao pagePermissionDao;
    private final UserContext userContext;

    @GetMapping("/ui/page/{pageId}/comments")
    public String listComments(@PathVariable("pageId") Integer pageId, Model model) {
        PageId pid = new PageId(pageId);
        UserDetails user = userContext.getUserContext();
        Integer currentUserId = getUserId(user);

        if (!pagePermissionDao.hasUserPermission(pid, currentUserId, PagePermission.PermissionLevel.VIEW)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        populateCommentsModel(model, pid, user, currentUserId);
        return "page/comments :: comments";
    }

    @PostMapping("/ui/page/{pageId}/comments")
    public String createComment(
            @PathVariable("pageId") Integer pageId,
            @RequestParam("content") String content,
            Model model
    ) {
        PageId pid = new PageId(pageId);
        UserDetails user = userContext.getUserContext();
        Integer currentUserId = requireUserId(user);

        if (!pagePermissionDao.hasUserPermission(pid, currentUserId, PagePermission.PermissionLevel.VIEW)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        String trimmed = content == null ? "" : content.trim();
        if (!trimmed.isEmpty()) {
            if (trimmed.length() > MAX_COMMENT_LENGTH) {
                trimmed = trimmed.substring(0, MAX_COMMENT_LENGTH);
            }
            pageCommentDao.create(pid, currentUserId, trimmed);
        }

        populateCommentsModel(model, pid, user, currentUserId);
        return "page/comments :: comments";
    }

    @DeleteMapping("/ui/page/{pageId}/comments/{commentId}")
    public String deleteComment(
            @PathVariable("pageId") Integer pageId,
            @PathVariable("commentId") Integer commentId,
            Model model
    ) {
        PageId pid = new PageId(pageId);
        UserDetails user = userContext.getUserContext();
        Integer currentUserId = requireUserId(user);

        Optional<PageComment> comment = pageCommentDao.findById(commentId);
        if (comment.isPresent()) {
            PageComment c = comment.get();
            // Sanity check: the comment must belong to this page.
            if (!c.getPageId().value().equals(pageId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            // Only the author or an admin may delete.
            boolean isOwner = c.getUserId() != null && c.getUserId().equals(currentUserId);
            if (!isOwner && !user.isAdmin()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            pageCommentDao.delete(commentId);
        }

        populateCommentsModel(model, pid, user, currentUserId);
        return "page/comments :: comments";
    }

    private void populateCommentsModel(Model model, PageId pid, UserDetails user, Integer currentUserId) {
        model.addAttribute("pageId", pid.value());
        model.addAttribute("comments", pageCommentDao.listForPage(pid));
        model.addAttribute("canComment", currentUserId != null);
        model.addAttribute("currentUserId", currentUserId);
        model.addAttribute("currentIsAdmin", user.isAdmin());
        model.addAttribute("currentInitial",
                user.isGuest() || user.getLogin() == null || user.getLogin().isEmpty()
                        ? "?"
                        : user.getLogin().substring(0, 1).toUpperCase());
    }

    private Integer getUserId(UserDetails user) {
        if (user.isGuest()) {
            return null;
        }
        try {
            return Integer.valueOf(user.getUserId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer requireUserId(UserDetails user) {
        Integer id = getUserId(user);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return id;
    }
}
