package org.knowledgeroot.app.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Validate before opening database or storage connections; never print credential values. */
@Configuration(proxyBeanMethods = false)
@Profile("production")
public class ProductionConfig {
    @Bean
    static BeanFactoryPostProcessor productionSettings(Environment environment) {
        return factory -> validate(environment);
    }

    static void validate(Environment env) {
        if (env.acceptsProfiles(Profiles.of("development"))) fail("Do not combine production and development profiles");
        String url = required(env, "spring.datasource.url");
        if (!url.startsWith("jdbc:mariadb://") || url.toLowerCase(java.util.Locale.ROOT).contains("createdatabaseifnotexist=true"))
            fail("Production requires an existing MariaDB database URL");
        String runtime = required(env, "spring.datasource.username");
        required(env, "spring.datasource.password");
        String migration = required(env, "knowledgeroot.migration.username");
        required(env, "knowledgeroot.migration.password");
        if (runtime.equalsIgnoreCase("root") || migration.equalsIgnoreCase("root") || runtime.equalsIgnoreCase(migration))
            fail("Use distinct non-root runtime and migration database users");
        for (String property : new String[]{"spring.thymeleaf.cache", "server.servlet.session.cookie.secure",
                "server.servlet.session.cookie.http-only"}) {
            if (!env.getProperty(property, Boolean.class, false)) fail("Production requires " + property + "=true");
        }
        if (env.getProperty("knowledgeroot.demo-data.enabled", Boolean.class, false)) fail("Production cannot use demo data");
        String sameSite = required(env, "server.servlet.session.cookie.same-site");
        if (!sameSite.equalsIgnoreCase("lax") && !sameSite.equalsIgnoreCase("strict")) fail("Production requires Lax or Strict session cookies");
        String strategy = env.getProperty("server.forward-headers-strategy", "none");
        if (!strategy.equalsIgnoreCase("none") && !strategy.equalsIgnoreCase("native"))
            fail("Production supports only none or native forwarding");
        if (strategy.equalsIgnoreCase("native")) {
            String pattern = required(env, "server.tomcat.remoteip.internal-proxies");
            java.util.regex.Pattern.compile(pattern);
            if (pattern.equals(".*") || pattern.equals("^.*$") || pattern.equals("(?!)"))
                fail("Configure the specific trusted proxy addresses before enabling native forwarding");
        }
    }

    private static String required(Environment env, String key) {
        String value = env.getProperty(key);
        if (value == null || value.isBlank()) fail("Missing production setting: " + key);
        return value;
    }
    private static void fail(String message) { throw new IllegalStateException(message); }
}
