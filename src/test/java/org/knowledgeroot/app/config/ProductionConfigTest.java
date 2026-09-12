package org.knowledgeroot.app.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;
import static org.junit.jupiter.api.Assertions.*;

class ProductionConfigTest {
    private MockEnvironment configured() throws Exception {
        var env = new MockEnvironment();
        env.setActiveProfiles("production");
        env.getPropertySources().addLast(new ResourcePropertySource("classpath:application-production.properties"));
        return env.withProperty("spring.datasource.url", "jdbc:mariadb://database:3306/knowledgeroot")
                .withProperty("spring.datasource.username", "runtime").withProperty("spring.datasource.password", "runtime-secret")
                .withProperty("knowledgeroot.migration.username", "migration").withProperty("knowledgeroot.migration.password", "migration-secret");
    }

    @Test void validProductionSettingsDoNotNeedAConnectionForValidation() throws Exception {
        var env = configured();
        new ApplicationContextRunner().withInitializer(context -> context.setEnvironment(env))
                .withUserConfiguration(ProductionConfig.class).run(context -> assertNull(context.getStartupFailure()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "knowledgeroot.migration.username", "knowledgeroot.migration.password"})
    void missingSettingsFailWithoutPrintingCredentials(String property) throws Exception {
        var env = configured().withProperty(property, "");
        var failure = assertThrows(IllegalStateException.class, () -> ProductionConfig.validate(env));
        assertTrue(failure.getMessage().contains(property));
        assertFalse(failure.getMessage().contains("secret"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.thymeleaf.cache", "server.servlet.session.cookie.secure", "server.servlet.session.cookie.http-only"})
    void unsafeOverridesFail(String property) throws Exception {
        var env = configured().withProperty(property, "false");
        assertThrows(IllegalStateException.class, () -> ProductionConfig.validate(env));
    }

    @Test void rootAndSharedDatabaseAccountsAreRejected() throws Exception {
        var env = configured().withProperty("spring.datasource.username", "root");
        assertThrows(IllegalStateException.class, () -> ProductionConfig.validate(env));
        env.setProperty("spring.datasource.username", "migration");
        assertThrows(IllegalStateException.class, () -> ProductionConfig.validate(env));
    }

    @Test void forwardingRequiresExplicitLimitedTrust() throws Exception {
        var env = configured().withProperty("server.forward-headers-strategy", "framework");
        assertThrows(IllegalStateException.class, () -> ProductionConfig.validate(env));
        env.setProperty("server.forward-headers-strategy", "native");
        assertThrows(IllegalStateException.class, () -> ProductionConfig.validate(env));
        env.setProperty("server.tomcat.remoteip.internal-proxies", ".*");
        assertThrows(IllegalStateException.class, () -> ProductionConfig.validate(env));
        env.setProperty("server.tomcat.remoteip.internal-proxies", "192[.]0[.]2[.]10");
        assertDoesNotThrow(() -> ProductionConfig.validate(env));
    }

    @Test void developmentCannotBeCombinedWithProduction() throws Exception {
        var env = configured();
        env.setActiveProfiles("development", "production");
        assertThrows(IllegalStateException.class, () -> ProductionConfig.validate(env));
    }
}
