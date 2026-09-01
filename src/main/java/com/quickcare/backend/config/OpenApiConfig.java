package com.quickcare.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI quickcareOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("QuickCare API")
                        .description("Backend do QuickCare, marketplace de profissionais de saude")
                        .version("v0.1"))
                .components(new Components()
                        .addSecuritySchemes("accessTokenCookie", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("access_token")));
    }

}
