package com.meetingroom.api.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Convierte el claim {@code roles} del token en autoridades {@code ROLE_*}.
 *
 * <p>El conversor por defecto solo entiende {@code scope}/{@code scp}, que es el modelo OAuth2
 * delegado; aqui los roles viajan en un claim propio porque el token lo emite la propia API.</p>
 */
@Component
public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        List<GrantedAuthority> authorities = roles == null
                ? List.of()
                : roles.stream()
                        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

}
