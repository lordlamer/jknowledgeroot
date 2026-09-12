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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
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

    @Bean
    protected SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        SecurityContextRepository contextRepository = new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
        return http
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .addFilterBefore(new SessionUserValidationFilter(userContext, contextRepository),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> {
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

                    auth.anyRequest().authenticated();
                })
                .formLogin(form -> form
                        .loginPage("/login")
                        .failureUrl("/login?error")
                        .loginProcessingUrl("/logmein")
                        .defaultSuccessUrl("/login/success", true)
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/logout/success")
                        .permitAll()
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                )
                .build();
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) {
        auth.authenticationProvider(authProvider);
    }
}
