package org.knowledgeroot.app.mcp.oauth;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.knowledgeroot.app.mcp.McpFeatureGateFilter;
import org.knowledgeroot.app.mcp.McpSettings;
import org.knowledgeroot.app.mcp.McpUrls;
import org.knowledgeroot.app.mcp.McpUserToken;
import org.knowledgeroot.app.security.auth.SessionUserValidationFilter;
import org.knowledgeroot.app.security.config.WebSecurityConfig;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.context.domain.UserNotFoundException;
import org.knowledgeroot.app.security.mfa.SecondFactorGateFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Set;

/**
 * OAuth 2.1 for the MCP server: Spring Authorization Server issues the tokens (authorization code
 * with PKCE, refresh token rotation, dynamic client registration), {@code /mcp} is a JWT resource
 * server. Users sign in with the normal form login including the second factor and grant scopes
 * on the consent page; the roles and permissions of the web UI apply to every tool call.
 */
@Configuration
@EnableScheduling
public class McpSecurityConfig {
    static final String REGISTRATION_PATH = "/oauth2/register";

    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbc) {
        return new JdbcRegisteredClientRepository(jdbc);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbc, clients);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }

    /** The issuer is the configured public URL, otherwise the origin of each request. */
    @Bean
    AuthorizationServerSettings authorizationServerSettings(McpUrls urls) {
        var settings = AuthorizationServerSettings.builder().clientRegistrationEndpoint(REGISTRATION_PATH);
        urls.configured().ifPresent(settings::issuer);
        return settings.build();
    }

    /**
     * Access tokens carry the user id as subject, the MCP endpoint as audience and a marker of
     * the current password. A refresh after a password change is refused, so an assistant has to
     * be connected again, like app sessions end on a password change.
     */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> mcpTokenCustomizer(UserContext users) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) return;
            UserDetails user;
            try {
                user = users.getUserContextForId(context.getPrincipal().getName());
            } catch (UserNotFoundException | NumberFormatException e) {
                throw signInAgain();
            }
            if (user.isGuest()) throw signInAgain();
            if (AuthorizationGrantType.REFRESH_TOKEN.equals(context.getAuthorizationGrantType())) {
                var previous = context.getAuthorization() == null ? null : context.getAuthorization().getAccessToken();
                Object marker = previous == null ? null : previous.getClaims().get(McpAuthenticationConverter.CREDENTIAL_CLAIM);
                if (!user.getCredentialTag().equals(marker)) throw signInAgain();
            }
            String issuer = context.getAuthorizationServerContext().getIssuer();
            // A plain list: the authorization table stores the claims as JSON with an allow-list of collection types.
            context.getClaims().audience(new java.util.ArrayList<>(List.of(issuer + McpUrls.MCP_PATH)))
                    .claim(McpAuthenticationConverter.CREDENTIAL_CLAIM, user.getCredentialTag())
                    .claim("login", user.getLogin())
                    .claim("cid", context.getRegisteredClient().getId());
        };
    }

    /** JWT access tokens plus refresh tokens, which the default generator would withhold from public clients. */
    @Bean
    OAuth2TokenGenerator<?> mcpTokenGenerator(McpSigningKeys keys, OAuth2TokenCustomizer<JwtEncodingContext> customizer) {
        var jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(keys));
        jwtGenerator.setJwtCustomizer(customizer);
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, new McpRefreshTokenGenerator());
    }

    private static OAuth2AuthenticationException signInAgain() {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                "Sign in to Knowledgeroot again and connect the assistant anew.", null));
    }

    /** The authorization server endpoints: authorize (browser, session), token, registration, JWKS and metadata. */
    @Bean
    @Order(1)
    SecurityFilterChain mcpAuthorizationServerChain(HttpSecurity http, McpSettings mcpSettings, UserContext userContext,
                                                   AuthorizationServerSettings settings, OAuth2TokenGenerator<?> tokenGenerator,
                                                   RegisteredClientRepository clients) throws Exception {
        SecurityContextRepository contextRepository = WebSecurityConfig.securityContextRepository();
        var authorizationServer = new OAuth2AuthorizationServerConfigurer();
        var htmlRequests = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        htmlRequests.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server
                        .authorizationServerSettings(settings)
                        .tokenGenerator(tokenGenerator)
                        .clientAuthentication(client -> client
                                .authenticationConverter(McpPublicClientRefresh.converter())
                                .authenticationProviders(providers -> providers.addFirst(McpPublicClientRefresh.provider(clients))))
                        .authorizationEndpoint(endpoint -> endpoint
                                .consentPage(McpConsentController.PATH)
                                .authorizationRequestConverters(converters -> converters.replaceAll(McpPrincipalConverter::new)))
                        .authorizationServerMetadataEndpoint(metadata -> metadata.authorizationServerMetadataCustomizer(builder -> builder
                                .claim("registration_endpoint", AuthorizationServerContextHolder.getContext().getIssuer() + REGISTRATION_PATH)
                                .claim("scopes_supported", List.of(McpUserToken.SCOPE_READ, McpUserToken.SCOPE_WRITE))))
                        .clientRegistrationEndpoint(registration -> registration
                                .openRegistrationAllowed(true)
                                .authenticationProviders(providers -> providers.forEach(provider -> {
                                    if (provider instanceof OAuth2ClientRegistrationAuthenticationProvider open) {
                                        open.setAuthenticationValidator(McpClientRegistration.validator(mcpSettings));
                                        open.setRegisteredClientConverter(McpClientRegistration.registeredClientConverter());
                                    }
                                }))))
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .addFilterBefore(new McpFeatureGateFilter(mcpSettings), SecurityContextHolderFilter.class)
                .addFilterBefore(new SessionUserValidationFilter(userContext, contextRepository), AnonymousAuthenticationFilter.class)
                .addFilterBefore(new SecondFactorGateFilter(), AuthorizationFilter.class)
                .cors(cors -> cors.configurationSource(corsConfiguration()))
                // Open registration needs no token; every other endpoint authenticates the user or the client itself.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, REGISTRATION_PATH).permitAll()
                        .anyRequest().authenticated())
                // Browsers arriving at /oauth2/authorize sign in first; assistants calling the token endpoint get a plain status.
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), htmlRequests));
        return http.build();
    }

    /** The MCP endpoint itself: stateless, bearer tokens only, read scope required for every call. */
    @Bean
    @Order(2)
    SecurityFilterChain mcpResourceChain(HttpSecurity http, McpSettings mcpSettings, McpUrls urls, UserContext userContext,
                                        JWKSource<SecurityContext> keys) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(keys).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.<OAuth2TokenValidator<Jwt>>of(
                JwtValidators.createDefault(), new McpTokenValidator(urls))));
        var entryPoint = new BearerTokenAuthenticationEntryPoint();
        entryPoint.setResourceMetadataParameterResolver(urls::resourceMetadata);
        http.securityMatcher(McpUrls.MCP_PATH, McpUrls.MCP_PATH + "/**",
                        McpUrls.RESOURCE_METADATA_PATH, McpUrls.RESOURCE_METADATA_PATH + "/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .requestCache(AbstractHttpConfigurer::disable)
                .addFilterBefore(new McpFeatureGateFilter(mcpSettings), SecurityContextHolderFilter.class)
                .cors(cors -> cors.configurationSource(corsConfiguration()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, McpUrls.RESOURCE_METADATA_PATH, McpUrls.RESOURCE_METADATA_PATH + "/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().hasAuthority("SCOPE_" + McpUserToken.SCOPE_READ))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(new McpAuthenticationConverter(userContext)))
                        .authenticationEntryPoint(entryPoint)
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(builder -> {
                            String origin = urls.currentOrigin().orElse("");
                            builder.resource(origin + McpUrls.MCP_PATH)
                                    .authorizationServers(servers -> { servers.clear(); servers.add(origin); })
                                    .scopes(scopes -> { scopes.clear(); scopes.add(McpUserToken.SCOPE_READ); scopes.add(McpUserToken.SCOPE_WRITE); })
                                    .resourceName("Knowledgeroot");
                        })));
        return http.build();
    }

    /** Assistants running in a browser read the metadata and tokens cross-origin; no cookies are involved. */
    private static UrlBasedCorsConfigurationSource corsConfiguration() {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("WWW-Authenticate", "Mcp-Session-Id", "Mcp-Protocol-Version"));
        configuration.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        for (String path : List.of(McpUrls.MCP_PATH, McpUrls.MCP_PATH + "/**", "/.well-known/**", "/oauth2/token",
                REGISTRATION_PATH, "/oauth2/jwks", "/oauth2/revoke")) {
            source.registerCorsConfiguration(path, configuration);
        }
        return source;
    }
}
