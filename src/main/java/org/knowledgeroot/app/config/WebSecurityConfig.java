package org.knowledgeroot.app.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {
    @Bean
    protected SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // configure login
        return http
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/ui/admin/**").hasRole("ADMIN");
                    auth.requestMatchers("/admin/**").hasRole("ADMIN");

                    auth.anyRequest().permitAll();
                })
                .formLogin()
                //.loginPage("/ui/login")
                //.failureUrl("/ui/login?error")
                //.loginProcessingUrl("/ui/logmein")
                //.defaultSuccessUrl("/ui/dashboard")
                //.usernameParameter("username")
                //.passwordParameter("password")
                .and()
                .logout()
                //.logoutSuccessUrl("/ui/login?logout")
                .and()
                .csrf()
                .disable()
                .build()
        ;
    }

    //@Autowired
    /*
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        //auth.authenticationProvider(authProvider);

        // memory auth
        auth
                .inMemoryAuthentication()
                .withUser("user").password(passwordEncoder().encode("password")).roles("USER")
                .and()
                .withUser("admin").password(passwordEncoder().encode("password")).roles("ADMIN");
*/

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        //auth.authenticationProvider(authProvider);

        // memory auth
        auth
                .inMemoryAuthentication()
                .withUser("user").password("{noop}password").roles("USER")
                .and()
                .withUser("admin").password("{noop}password").roles("ADMIN");

    }

    /*
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
     */
}
