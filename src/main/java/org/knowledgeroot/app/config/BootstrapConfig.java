package org.knowledgeroot.app.config;

import org.knowledgeroot.app.setup.InitialAdministrator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

@Configuration
public class BootstrapConfig {
    @Bean(initMethod = "initialize")
    @DependsOn("liquibase")
    InitialAdministrator initialAdministrator(DataSource dataSource, Environment environment) {
        return new InitialAdministrator(dataSource, LiquibaseConfig.demoDataEnabled(environment),
                environment.getProperty("knowledgeroot.bootstrap.login", ""),
                environment.getProperty("knowledgeroot.bootstrap.password", ""));
    }
}
