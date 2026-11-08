package org.knowledgeroot.app.page.collab;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Live collaboration (R63): every open editor of a page keeps one WebSocket under
 * {@code /ui/page/{id}/collab}. The handshake runs through the normal security filter chain, so
 * the browser's session cookie identifies the user; {@link CollaborationHandshake} then checks
 * the origin and the edit permission before the socket opens.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
class CollaborationConfig implements WebSocketConfigurer {
    /** A Yjs update of a pasted page or a compact snapshot; the editor refuses larger frames. */
    static final int MAX_FRAME_BYTES = 512 * 1024;
    private final CollaborationHandler handler;
    private final CollaborationHandshake handshake;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Same-origin only: the interceptor compares the Origin header with the request's own origin.
        registry.addHandler(handler, "/ui/page/*/collab").addInterceptors(handshake).setAllowedOriginPatterns("*");
    }

    /** Tomcat's default frame limit is 8 KB; tests without a real servlet container have no WebSocket container. */
    @Bean
    ServletServerContainerFactoryBean collaborationContainer(jakarta.servlet.ServletContext servletContext) {
        if (servletContext.getAttribute("jakarta.websocket.server.ServerContainer") == null) return null;
        var container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(MAX_FRAME_BYTES);
        container.setMaxTextMessageBufferSize(8 * 1024);
        container.setMaxSessionIdleTimeout(5L * 60 * 1000);
        return container;
    }
}
