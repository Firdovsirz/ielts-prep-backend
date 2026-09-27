package com.ieltsprep.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ieltsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("IELTS Prep API")
                        .version("1.0.0")
                        .description("Practice generation, grading, progress tracking and study planning."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }

    /**
     * Response/document schemas: every property is present in the JSON (Jackson includes nulls), so mark them all
     * required; properties that can be null carry {@code @Schema(nullable = true)}. Request bodies (names ending in
     * "Request") keep optional properties. This keeps the generated TypeScript types exact.
     */
    @Bean
    OpenApiCustomizer requiredPropertiesCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            for (Map.Entry<String, Schema> e : openApi.getComponents().getSchemas().entrySet()) {
                Schema<?> schema = e.getValue();
                if (e.getKey().endsWith("Request") || schema.getProperties() == null) {
                    continue;
                }
                schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
            }
        };
    }
}
