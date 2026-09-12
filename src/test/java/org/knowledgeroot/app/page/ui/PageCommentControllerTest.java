package org.knowledgeroot.app.page.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.knowledgeroot.app.page.domain.PageComment;
import org.knowledgeroot.app.page.domain.PageCommentDao;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.knowledgeroot.app.page.domain.PagePermission.PermissionLevel.VIEW;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PageCommentControllerTest {
    @Mock private PageCommentDao comments;
    @Mock private PagePermissionDao permissions;
    @Mock private UserContext userContext;
    private PageCommentController controller;

    @BeforeEach
    void setUp() {
        controller = new PageCommentController(comments, permissions, userContext);
        when(userContext.getUserContext()).thenReturn(UserDetails.builder()
                .userId("2").login("reader").role(UserDetails.Role.USER).build());
    }

    @Test
    void deleteWithGuessedCommentIdCannotRevealPrivatePageComments() {
        ExtendedModelMap model = new ExtendedModelMap();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.deleteComment(99, 12345, model));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertTrue(model.isEmpty());
        verifyNoInteractions(comments);
    }

    @Test
    void ownerCanDeleteAndReloadCommentsOnReadablePage() {
        when(permissions.hasUserPermission(new PageId(10), 2, VIEW)).thenReturn(true);
        when(comments.findById(7)).thenReturn(Optional.of(comment(10, 2)));
        when(comments.listForPage(new PageId(10))).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("page/comments :: comments", controller.deleteComment(10, 7, model));
        verify(comments).delete(7);
        assertEquals(List.of(), model.get("comments"));
    }

    @Test
    void readablePageDoesNotAllowDeletingAnotherAuthorsComment() {
        when(permissions.hasUserPermission(new PageId(10), 2, VIEW)).thenReturn(true);
        when(comments.findById(7)).thenReturn(Optional.of(comment(10, 3)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.deleteComment(10, 7, new ExtendedModelMap()));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(comments, never()).delete(any());
        verify(comments, never()).listForPage(any());
    }

    @Test
    void commentFromAnotherPageCannotBeDeletedThroughReadablePage() {
        when(permissions.hasUserPermission(new PageId(10), 2, VIEW)).thenReturn(true);
        when(comments.findById(7)).thenReturn(Optional.of(comment(99, 2)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.deleteComment(10, 7, new ExtendedModelMap()));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(comments, never()).delete(any());
    }

    private PageComment comment(int pageId, int userId) {
        return PageComment.builder().id(7).pageId(new PageId(pageId)).userId(userId).content("comment").build();
    }
}
