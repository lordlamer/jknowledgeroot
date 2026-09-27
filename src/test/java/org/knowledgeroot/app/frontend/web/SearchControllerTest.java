package org.knowledgeroot.app.frontend.web;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.page.domain.*;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchControllerTest {
    private final PageDao pages = mock(PageDao.class);
    private final UserContext users = mock(UserContext.class);
    private final HtmxRequest request = mock(HtmxRequest.class);
    private final SearchController controller = new SearchController(pages, users);

    @Test
    void searchUsesVisibleWindowAndOnlyDisplaysTwentyShortTextSnippets() {
        when(users.getUserContext()).thenReturn(UserDetails.builder().role(UserDetails.Role.GUEST).build());
        when(pages.listVisiblePages(any(), isNull())).thenReturn(IntStream.rangeClosed(1, 21)
                .mapToObj(id -> Page.builder().pageId(new PageId(id)).name("page")
                        .content("<p>" + "text ".repeat(100) + "</p>").build()).toList());
        var model = new ExtendedModelMap();
        assertEquals("search/search", controller.showSearch(" wiki ", 20, model, request));
        verify(pages).listVisiblePages(argThat(filter -> filter.getStart() == 20 && filter.getLimit() == 21
                && filter.getQuery().equals("wiki")), isNull());
        var hits = (List<SearchController.SearchHit>) model.get("pages");
        assertEquals(20, hits.size());
        assertEquals(301, hits.getFirst().snippet().length());
        assertFalse(hits.getFirst().snippet().contains("<p>"));
        assertEquals(true, model.get("hasNext"));
        assertEquals(0, model.get("previousStart"));
        assertEquals(40, model.get("nextStart"));
    }

    @Test
    void snippetsAreCentredOnTheMatchAndHighlightedCaseInsensitively() {
        String snippet = SearchController.snippet("<p>" + "lead ".repeat(200) + "the Needle here " + "tail ".repeat(200) + "</p>", "needle");
        assertTrue(snippet.startsWith("…") && snippet.endsWith("…"), snippet);
        assertTrue(snippet.contains("the Needle here"));
        assertTrue(snippet.length() <= 302);
        assertEquals(List.of(new SearchController.Part("A ", false), new SearchController.Part("wiki", true),
                        new SearchController.Part(" and ", false), new SearchController.Part("WIKI", true)),
                SearchController.highlight("A wiki and WIKI", "wiki"));
        assertEquals(List.of(new SearchController.Part("<b>", false)), SearchController.highlight("<b>", "x"));
    }

    @Test
    void resultPathsStopAtHiddenAncestorsAndReuseLookups() {
        when(users.getUserContext()).thenReturn(UserDetails.builder().role(UserDetails.Role.GUEST).build());
        when(pages.listVisiblePages(any(), isNull())).thenReturn(List.of(
                Page.builder().pageId(new PageId(10)).parent(3).name("One").content("wiki").build(),
                Page.builder().pageId(new PageId(11)).parent(3).name("Two").content("wiki").build()));
        when(pages.findNavigationPage(new PageId(3), null)).thenReturn(java.util.Optional.of(
                Page.builder().pageId(new PageId(3)).parent(2).name("Guides").build()));
        when(pages.findNavigationPage(new PageId(2), null)).thenReturn(java.util.Optional.empty());
        var model = new ExtendedModelMap();
        controller.showSearch("wiki", 0, model, request);
        var hits = (List<SearchController.SearchHit>) model.get("pages");
        assertEquals(List.of("Guides"), hits.get(0).path());
        assertEquals(List.of("Guides"), hits.get(1).path());
        verify(pages, times(1)).findNavigationPage(new PageId(3), null);
    }

    @Test
    void blankSearchDoesNotReadAllPages() {
        when(users.getUserContext()).thenReturn(UserDetails.builder().role(UserDetails.Role.GUEST).build());
        var model = new ExtendedModelMap();
        controller.showSearch(" ", 0, model, request);
        assertEquals(List.of(), model.get("pages"));
        verifyNoInteractions(pages);
    }

    @Test
    void invalidSearchLimitsAreRejectedBeforeQuery() {
        assertThrows(ResponseStatusException.class, () -> controller.showSearch("x".repeat(201), 0, new ExtendedModelMap(), request));
        assertThrows(ResponseStatusException.class, () -> controller.showSearch("x", -1, new ExtendedModelMap(), request));
        verifyNoInteractions(pages);
    }
}
