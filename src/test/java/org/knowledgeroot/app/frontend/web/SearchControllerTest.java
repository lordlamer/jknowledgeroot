package org.knowledgeroot.app.frontend.web;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.page.db.PageImpl;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchControllerTest {

    @Test
    void searchShouldOnlyReturnPagesWithViewPermission() {
        PageImpl pageDao = mock(PageImpl.class);
        PagePermissionDao permissionDao = mock(PagePermissionDao.class);
        UserContext userContext = mock(UserContext.class);
        when(userContext.getUserContext()).thenReturn(guestUser());

        Page visiblePage = Page.builder().pageId(new PageId(1)).name("visible").build();
        Page hiddenPage = Page.builder().pageId(new PageId(2)).name("hidden").build();
        when(pageDao.searchContent("wiki")).thenReturn(List.of(visiblePage, hiddenPage));

        when(permissionDao.hasUserPermission(eq(visiblePage.getPageId()), any(), eq(PagePermission.PermissionLevel.VIEW)))
                .thenReturn(true);
        when(permissionDao.hasUserPermission(eq(hiddenPage.getPageId()), any(), eq(PagePermission.PermissionLevel.VIEW)))
                .thenReturn(false);

        SearchController controller = new SearchController(pageDao, permissionDao, userContext);
        Model model = new ExtendedModelMap();
        HtmxRequest htmxRequest = mock(HtmxRequest.class);
        when(htmxRequest.isHtmxRequest()).thenReturn(false);

        String view = controller.showSearch("wiki", model, htmxRequest);

        assertEquals("search/search", view);
        Object pages = model.getAttribute("pages");
        assertNotNull(pages);
        assertEquals(1, ((List<?>) pages).size());
    }

    private UserDetails guestUser() {
        return UserDetails.builder()
                .userId("guest")
                .login("guest")
                .email("")
                .firstName("Guest")
                .surName("")
                .role(UserDetails.Role.GUEST)
                .language("en")
                .build();
    }
}
