package com.meetingroom.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private final String version;

    public OpenApiConfig(@Value("${app.version:0.0.1}") String version) {
        this.version = version;
    }

    @Bean
    public OpenAPI meetingRoomOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Meeting Room API")
                .version(version)
                .description("API REST para la gestion de salas de reunion y sus reservas"));
    }

}
