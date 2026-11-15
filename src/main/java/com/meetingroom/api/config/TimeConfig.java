package com.meetingroom.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * El reloj se inyecta para que las reglas que dependen de "ahora" se puedan probar con una
 * hora fija.
 */
@Configuration
@EnableConfigurationProperties(BookingProperties.class)
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

}
