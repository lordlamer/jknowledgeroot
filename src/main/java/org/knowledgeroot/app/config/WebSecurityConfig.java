package org.knowledgeroot.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .antMatchers("/ui/admin/**").hasRole("ADMIN")
                .antMatchers("/admin/**").hasRole("ADMIN")

                .anyRequest().permitAll()
                .and()
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
        ;
    }

    protected void configure(final AuthenticationManagerBuilder auth) throws Exception {
        //auth.authenticationProvider(authProvider);

        // memory auth
        auth
                .inMemoryAuthentication()
                .withUser("user").password(passwordEncoder().encode("password")).roles("USER")
                .and()
                .withUser("admin").password(passwordEncoder().encode("password")).roles("ADMIN");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}