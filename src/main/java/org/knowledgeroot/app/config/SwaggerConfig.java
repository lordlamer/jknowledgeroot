package org.knowledgeroot.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
public class SwaggerConfig {
    private String applicationName = "Knowledgeroot";
    private String buildVersion = "DEV";

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.basePackage("org.knowledgeroot"))
                .paths(PathSelectors.ant("/**"))
                .build()
                .apiInfo(apiInfo());
    }

    private ApiInfo apiInfo() {
        Contact contact = new Contact(
                "Knowledgeroot",
                "http://www.knowledgeroot.org",
                "info@knowledgeroot.org"
        );

        return new ApiInfo(
                applicationName,
                "Knowledgeroot API",
                buildVersion,
                "Terms of service",
                contact,
                "License of API",
                "https://github.com/lordlamer/jknowledgeroot/blob/master/LICENSE"
        );
    }
}
