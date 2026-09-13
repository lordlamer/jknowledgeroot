package org.knowledgeroot.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.HttpSessionIdResolver;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProbeSessionIdResolverTest {
    @Test void probesIgnoreSessionCookiesWhileNormalRequestsRetainCookieHandling() {
        var delegate = mock(HttpSessionIdResolver.class);
        var resolver = new ProbeSessionIdResolver(delegate);
        var request = new MockHttpServletRequest("GET", "/knowledge/actuator/health");
        request.setContextPath("/knowledge");
        for (String path : List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
            request.setRequestURI("/knowledge" + path);
            assertEquals(List.of(), resolver.resolveSessionIds(request));
        }
        verifyNoInteractions(delegate);
        when(delegate.resolveSessionIds(request)).thenReturn(List.of("existing-session"));
        request.setRequestURI("/knowledge/actuator/metrics");
        assertEquals(List.of("existing-session"), resolver.resolveSessionIds(request));
        request.setRequestURI("/knowledge/actuator/health");
        request.setMethod("POST");
        assertEquals(List.of("existing-session"), resolver.resolveSessionIds(request));
        var response = new MockHttpServletResponse();
        resolver.setSessionId(request, response, "new-session");
        resolver.expireSession(request, response);
        verify(delegate).setSessionId(request, response, "new-session");
        verify(delegate).expireSession(request, response);
    }
}
