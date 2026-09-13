package org.knowledgeroot.app.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.HttpSessionIdResolver;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
class MonitoringConfig {
    @Bean
    // Expose the cookie resolver type so Boot retains its configured cookie serializer.
    CookieHttpSessionIdResolver cookieSessionIdResolver(CookieSerializer cookieSerializer) {
        var resolver = new CookieHttpSessionIdResolver();
        resolver.setCookieSerializer(cookieSerializer);
        return resolver;
    }

    @Bean
    @Primary
    HttpSessionIdResolver httpSessionIdResolver(CookieHttpSessionIdResolver cookieSessionIdResolver) {
        return new ProbeSessionIdResolver(cookieSessionIdResolver);
    }

    @Bean
    DependencyHealthIndicator databaseHealthIndicator(DataSource source, MeterRegistry metrics) {
        return new DependencyHealthIndicator("database", () -> {
            try (var connection = source.getConnection()) {
                int previousTimeout = connection.getNetworkTimeout();
                try {
                    connection.setNetworkTimeout(Runnable::run, 2000);
                    try (var statement = connection.createStatement()) {
                        statement.setQueryTimeout(2);
                        try (var result = statement.executeQuery("SELECT 1")) {
                            if (!result.next() || result.getInt(1) != 1) throw new IllegalStateException("Database probe failed");
                        }
                    }
                } finally { connection.setNetworkTimeout(Runnable::run, previousTimeout); }
            } catch (java.sql.SQLException failure) { throw new IllegalStateException("Database probe failed", failure); }
        }, metrics);
    }
}
