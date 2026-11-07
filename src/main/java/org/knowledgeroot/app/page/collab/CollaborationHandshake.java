package org.knowledgeroot.app.page.collab;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Admits a live-collaboration socket only from the application's own origin and only for users
 * who may edit the page. The user comes from the session the browser sends with the handshake;
 * guests with an edit grant are allowed like in the editor itself.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class CollaborationHandshake implements HandshakeInterceptor {
    static final String PAGE = "collab.page";
    static final String USER = "collab.user";
    static final String LOGIN = "collab.login";
    private static final Pattern PATH = Pattern.compile("/ui/page/(\\d+)/collab$");
    private final PagePermissionDao permissions;
    private final UserContext users;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        Matcher path = PATH.matcher(request.getURI().getPath());
        if (!path.find()) return reject(response, HttpStatus.NOT_FOUND);
        if (!(request instanceof ServletServerHttpRequest servlet)) return reject(response, HttpStatus.FORBIDDEN);
        String origin = request.getHeaders().getFirst("Origin");
        String own = UriComponentsBuilder.fromUriString(UrlUtils.buildFullRequestUrl(servlet.getServletRequest()))
                .replacePath(null).replaceQuery(null).fragment(null).build().toUriString();
        if (origin == null || !origin.equalsIgnoreCase(own)) {
            log.debug("Collaboration socket refused: origin {} is not {}", origin, own);
            return reject(response, HttpStatus.FORBIDDEN);
        }
        UserDetails user = users.getUserContext();
        Integer userId = user.isGuest() ? null : Integer.valueOf(user.getUserId());
        PageId page = new PageId(Integer.parseInt(path.group(1)));
        if (!permissions.hasUserPermission(page, userId, PagePermission.PermissionLevel.EDIT)) return reject(response, HttpStatus.FORBIDDEN);
        attributes.put(PAGE, page.value());
        attributes.put(USER, userId);
        attributes.put(LOGIN, user.isGuest() || user.getLogin() == null ? "guest" : user.getLogin());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception exception) { }

    private static boolean reject(ServerHttpResponse response, HttpStatus status) {
        response.setStatusCode(status);
        return false;
    }
}
