package com.meetingroom.api.dto;

/**
 * @param accessToken token que debe enviarse en la cabecera {@code Authorization: Bearer}
 * @param tokenType   siempre {@code Bearer}
 * @param expiresIn   segundos de vida restantes del token
 */
public record TokenResponse(

        String accessToken,
        String tokenType,
        long expiresIn,
        String username,
        String role

) {
}
