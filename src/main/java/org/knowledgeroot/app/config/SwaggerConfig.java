package org.knowledgeroot.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
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
    private static final String APPLICATION_NAME = "Knowledgeroot";
    private static final String BUILD_VERSION = "DEV";

    @Bean
    public Docket swaggerSpringMvcPlugin() {
        return new Docket(DocumentationType.SWAGGER_2)
                .useDefaultResponseMessages(false)
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("org.knowledgeroot"))
                .paths(PathSelectors.ant("/**"))
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title(APPLICATION_NAME)
                .description("Knowledgeroot API")
                .contact(new Contact(APPLICATION_NAME, "http://www.knowledgeroot.org", "info@knowledgeroot.org"))
                .license("BSD 2-Clause License")
                .licenseUrl("https://github.com/lordlamer/jknowledgeroot/blob/master/LICENSE")
                .version(BUILD_VERSION)
                .build();
    }
}
