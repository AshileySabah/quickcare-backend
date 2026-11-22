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
                        .description("API do QuickCare, plataforma que conecta pacientes a profissionais de saúde por meio de solicitações. "
                                + "Para chamar os endpoints com cadeado, faça POST /auth/login, copie o valor do "
                                + "cabeçalho X-Auth-Token da resposta e cole em Authorize.")
                        .version("v0.1"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token do cabeçalho X-Auth-Token devolvido pelo login.")));
    }

}
