package com.meetingroom.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * El usuario en memoria que genera Spring Boot no se usa: la autenticacion se
 * implementara con un mecanismo propio en un incremento posterior.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MeetingRoomApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeetingRoomApiApplication.class, args);
    }

}
