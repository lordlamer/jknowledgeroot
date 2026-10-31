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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SidebarControllerTest {
    @Mock private PageDao pages;
    @Mock private PageStarDao stars;
    @Mock private UserContext userContext;
    @Mock private org.knowledgeroot.app.space.CurrentSpace currentSpace;
    private SidebarController controller;

    @BeforeEach
    void setUp() {
        when(userContext.getUserContext()).thenReturn(UserDetails.builder()
                .userId("guest").login("guest").role(UserDetails.Role.GUEST).build());
        lenient().when(currentSpace.currentId()).thenReturn(1);
        controller = new SidebarController(pages, stars, userContext, currentSpace);
    }

    @Test
    void guestSeesOnlyReadablePagesAndNoGuessedPrivatePage() {
        when(pages.findNavigationPage(new PageId(99), null)).thenReturn(Optional.empty());
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("sidebar :: single", controller.index(model, null, 99, 0, null));

        assertEquals(List.of(), model.get("pages"));
        assertNull(model.get("singlePage"));
        verifyNoInteractions(stars);
        verify(pages, never()).listPages(any());
        verify(pages, never()).listNavigationPages(anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void expandingPrivateParentDoesNotExposeItsMetadata() {
        when(pages.findNavigationPage(new PageId(99), null)).thenReturn(Optional.empty());
        ExtendedModelMap model = new ExtendedModelMap();

        controller.index(model, 99, null, 0, null);

        assertNull(model.get("parentPage"));
        assertEquals(List.of(), model.get("pages"));
        verify(pages, never()).listNavigationPages(anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void nextSegmentUsesLastVisibleIdAndOnlyRendersFiftyEntries() {
        var rows = java.util.stream.IntStream.rangeClosed(20, 70).mapToObj(this::page).toList();
        when(pages.listNavigationPages(1, 0, 19, null)).thenReturn(rows);
        var model = new ExtendedModelMap();
        assertEquals("fragments/sidebar-entries :: entries", controller.index(model, 0, null, 19, null));
        assertEquals(rows.subList(0, 50), model.get("pages"));
        assertEquals(69, model.get("nextAfter"));
        assertEquals(true, model.get("hasNext"));
        verify(pages, never()).findById(any());
    }

    private Page page(int id) {
        return Page.builder().pageId(new PageId(id)).name("page-" + id).build();
    }
}
