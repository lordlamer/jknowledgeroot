package org.knowledgeroot.app.frontend.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.knowledgeroot.app.page.domain.*;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.knowledgeroot.app.page.domain.PagePermission.PermissionLevel.VIEW;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SidebarControllerTest {
    @Mock private PageDao pages;
    @Mock private PagePermissionDao permissions;
    @Mock private PageStarDao stars;
    @Mock private UserContext userContext;
    private SidebarController controller;

    @BeforeEach
    void setUp() {
        when(userContext.getUserContext()).thenReturn(UserDetails.builder()
                .userId("guest").login("guest").role(UserDetails.Role.GUEST).build());
        controller = new SidebarController(pages, permissions, stars, userContext);
    }

    @Test
    void guestSeesOnlyReadablePagesAndNoGuessedPrivatePage() {
        Page visible = page(10);
        Page hidden = page(99);
        when(pages.listPages(any())).thenReturn(List.of(visible, hidden));
        when(pages.findById(new PageId(99))).thenReturn(hidden);
        when(permissions.hasUserPermission(new PageId(10), null, VIEW)).thenReturn(true);
        ExtendedModelMap model = new ExtendedModelMap();

        controller.index(model, null, 99);

        assertEquals(List.of(visible), model.get("pages"));
        assertNull(model.get("singlePage"));
        verifyNoInteractions(stars);
    }

    @Test
    void expandingPrivateParentDoesNotExposeItsMetadata() {
        when(pages.listPages(any())).thenReturn(List.of());
        when(pages.findById(new PageId(99))).thenReturn(page(99));
        ExtendedModelMap model = new ExtendedModelMap();

        controller.index(model, 99, null);

        assertNull(model.get("parentPage"));
        assertEquals(List.of(), model.get("pages"));
    }

    private Page page(int id) {
        return Page.builder().pageId(new PageId(id)).name("page-" + id).build();
    }
}
