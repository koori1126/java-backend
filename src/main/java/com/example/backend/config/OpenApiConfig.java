package com.example.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI (/swagger-ui.html) で表示されるAPI情報の定義。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI backendApiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Backend API")
                        .description("Spring Boot backend API skeleton (EDB Oracle compatibility mode)")
                        .version("v1"));
    }
}
