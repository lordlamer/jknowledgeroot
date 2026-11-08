package org.knowledgeroot.app.security.config;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.security.auth.DatabaseAuthentificationProvider;
import org.knowledgeroot.app.security.auth.SessionUserValidationFilter;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {
    private final DatabaseAuthentificationProvider authProvider;
    private final UserContext userContext;
    /** Absent in slice tests without the MFA beans; then the password alone signs in. */
    private final org.springframework.beans.factory.ObjectProvider<org.knowledgeroot.app.security.mfa.SecondFactorGate> secondFactorGate;
    /** Sign-in through an OpenID Connect provider (R64); absent in slice tests. */
    private final org.springframework.beans.factory.ObjectProvider<org.knowledgeroot.app.security.oidc.OidcClientRegistrations> oidcRegistrations;
    private final org.springframework.beans.factory.ObjectProvider<org.knowledgeroot.app.security.oidc.LinkedOidcUserService> oidcUsers;
    private final org.springframework.beans.factory.ObjectProvider<org.knowledgeroot.app.security.oidc.OidcLoginSuccessHandler> oidcSuccess;
    private final org.springframework.beans.factory.ObjectProvider<org.knowledgeroot.app.security.oidc.OidcLoginFailureHandler> oidcFailure;

    @Bean
    @org.springframework.core.annotation.Order(0)
    SecurityFilterChain healthSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")
                // These routes only accept GET; no session-backed CSRF token is needed.
                .csrf(csrf -> csrf.disable())
                // AnonymousAuthenticationFilter builds session-based WebAuthenticationDetails.
                .anonymous(anonymous -> anonymous.disable())
                // Even STATELESS SessionManagementFilter inspects requested session IDs.
                .sessionManagement(session -> session.disable())
                .securityContext(context -> context.securityContextRepository(new org.springframework.security.web.context.NullSecurityContextRepository()))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    protected SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        SecurityContextRepository contextRepository = securityContextRepository();
        http
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .addFilterBefore(new SessionUserValidationFilter(userContext, contextRepository),
                        AnonymousAuthenticationFilter.class)
                // A session that still owes the second factor stays on /mfa (or gets 401 on the API).
                .addFilterBefore(new org.knowledgeroot.app.security.mfa.SecondFactorGateFilter(),
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/**").hasRole("ADMIN");
                    // Client API: guests may read public pages like in the web UI; the controllers
                    // apply page permissions and reject guest writes themselves.
                    auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
                    auth.requestMatchers("/api/v1/**").permitAll();
                    // Star endpoints — require an authenticated (non-guest) user. Must come
                    // before the /ui/page/** permitAll rule since the first match wins.
                    auth.requestMatchers(HttpMethod.POST,   "/ui/page/*/star").hasAnyRole("USER", "ADMIN");
                    auth.requestMatchers(HttpMethod.DELETE, "/ui/page/*/star").hasAnyRole("USER", "ADMIN");
                    auth.requestMatchers(                   "/ui/sidebar/starred").hasAnyRole("USER", "ADMIN");

                    // Comments — anyone may read, only authenticated users may post or delete.
                    auth.requestMatchers(HttpMethod.POST,   "/ui/page/*/comments").hasAnyRole("USER", "ADMIN");
                    auth.requestMatchers(HttpMethod.DELETE, "/ui/page/*/comments/**").hasAnyRole("USER", "ADMIN");

                    auth.requestMatchers(
                            "/",
                            "/favicon.ico",
                            "/webjars/**",
                            "/resources/**",
                            "/login",
                            "/login/oidc",
                            "/account/forgot-password",
                            "/account/reset-password",
                            "/account/verify-email",
                            "/login/success",
                            "/logout/success",
                            "/ui/welcome",
                            "/ui/sidebar",
                            "/ui/page/**",
                            "/ui/file/**",
                            "/search",
                            "/help",
                            "/about"
                    ).permitAll();
                    // Space overviews and switching are open to guests of public spaces; creating and managing need a sign-in.
                    auth.requestMatchers("/spaces/new", "/spaces/*/settings", "/spaces/*/archive", "/spaces/*/permissions/**").authenticated();
                    auth.requestMatchers(HttpMethod.GET, "/spaces", "/spaces/open", "/spaces/*").permitAll();

                    auth.requestMatchers("/ui/admin/**", "/admin/**").hasRole("ADMIN");

                    auth.requestMatchers("/user/**", "/group/**").hasRole("ADMIN");

                    auth.requestMatchers(HttpMethod.GET, "/page/**", "/file/**")
                            .hasAnyRole("USER", "ADMIN");
                    auth.requestMatchers(HttpMethod.GET, "/download/**")
                            .hasAnyRole("USER", "ADMIN");
                    auth.requestMatchers(HttpMethod.POST, "/page/**", "/file/**")
                            .hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.PUT, "/page/**", "/file/**")
                            .hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.DELETE, "/page/**", "/file/**")
                            .hasRole("ADMIN");

                    auth.requestMatchers("/api/**").hasAnyRole("USER", "ADMIN");
                    auth.requestMatchers("/profile/**").authenticated();
                    // Second step of the sign-in: reachable with the pending token, which has no role.
                    auth.requestMatchers("/mfa", "/mfa/**").authenticated();

                    auth.anyRequest().authenticated();
                })
                .formLogin(form -> form
                        .loginPage("/login")
                        .failureUrl("/login?error")
                        .loginProcessingUrl("/logmein")
                        .successHandler(new org.knowledgeroot.app.security.mfa.SecondFactorLoginSuccessHandler(
                                secondFactorGate.getIfAvailable()))
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/logout/success")
                        .permitAll()
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                )
                // App clients get a status code instead of the browser login redirect.
                // A single registered entry point would become the default for every route,
                // so the browser login redirect is registered explicitly for everything else.
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/v1/**"))
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
                                AnyRequestMatcher.INSTANCE));
        // OpenID Connect (R64): the provider's user ends up in the session as the same token a password sign-in
        // produces. The registration is read lazily, so an unreachable provider does not stop the application.
        var registrations = oidcRegistrations.getIfAvailable();
        if (registrations != null) {
            http.oauth2Login(login -> login
                    .loginPage("/login")
                    .clientRegistrationRepository(registrations)
                    .authorizedClientService(new org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService(registrations))
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUsers.getObject()))
                    .successHandler(oidcSuccess.getObject())
                    .failureHandler(oidcFailure.getObject()));
        }
        return http.build();
    }

    /** Session-backed security context storage, shared with the client API login. */
    public static SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    /** Stateless cookie repository; separate instances with these settings are interchangeable. */
    public static CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) {
        auth.authenticationProvider(authProvider);
    }
}
