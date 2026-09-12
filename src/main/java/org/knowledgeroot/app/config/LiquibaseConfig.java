package org.knowledgeroot.app.config;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import javax.sql.DataSource;

@Configuration
public class LiquibaseConfig {
    @Bean
    public SpringLiquibase liquibase(DataSource dataSource, Environment environment) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:dbupdates/changelog.xml");
        liquibase.setContexts(demoDataEnabled(environment) ? "production,development" : "production");
        liquibase.setShouldRun(true);

        return liquibase;
    }

    static boolean demoDataEnabled(Environment environment) {
        boolean enabled = environment.getProperty("knowledgeroot.demo-data.enabled", Boolean.class, false);
        if (enabled && !environment.acceptsProfiles(Profiles.of("development & !production"))) {
            throw new IllegalStateException("Demo data requires the development profile without the production profile");
        }
        return enabled;
    }
}
