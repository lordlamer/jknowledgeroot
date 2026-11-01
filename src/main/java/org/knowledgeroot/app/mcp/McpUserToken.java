package org.knowledgeroot.app.mcp;

import org.knowledgeroot.app.security.auth.KnowledgerootUserToken;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The identity of an MCP request: the user behind the access token with the roles of a web
 * session, plus the scopes the user granted to the assistant. Domain services see a signed-in
 * user as usual; the tools check the scopes.
 */
public class McpUserToken extends KnowledgerootUserToken {
    @Serial
    private static final long serialVersionUID = 4318220046118201907L;
    public static final String SCOPE_READ = "mcp:read";
    public static final String SCOPE_WRITE = "mcp:write";
    private final Set<String> scopes;
    private final List<GrantedAuthority> authorities;
    private final transient Jwt jwt;
    /** Registered client the token was issued to (claim {@code cid}), for the audit. */
    private final String clientId;

    public McpUserToken(UserDetails user, Collection<String> scopes, Jwt jwt) {
        super(user);
        this.scopes = new LinkedHashSet<>(scopes);
        this.jwt = jwt;
        this.clientId = jwt == null ? null : jwt.getClaimAsString("cid");
        var all = new ArrayList<GrantedAuthority>(super.getAuthorities());
        this.scopes.forEach(scope -> all.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
        this.authorities = List.copyOf(all);
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        // The constructor of the parent asks before the field exists.
        return authorities == null ? super.getAuthorities() : authorities;
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public String getClientId() {
        return clientId;
    }
}
