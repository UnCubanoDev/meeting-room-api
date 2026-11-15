package com.meetingroom.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    private final String version;

    public OpenApiConfig(@Value("${app.version:0.0.1}") String version) {
        this.version = version;
    }

    /**
     * Declara el esquema de seguridad para que Swagger UI muestre el boton "Authorize" y
     * envie {@code Authorization: Bearer <token>} en las peticiones protegidas.
     */
    @Bean
    public OpenAPI meetingRoomOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Meeting Room API")
                        .version(version)
                        .description("API REST para la gestion de salas de reunion y sus reservas"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

}
