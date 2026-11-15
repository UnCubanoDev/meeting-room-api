package com.meetingroom.api.service;

import com.meetingroom.api.dto.LoginRequest;
import com.meetingroom.api.dto.TokenResponse;
import com.meetingroom.api.entity.AppUser;
import com.meetingroom.api.exception.InvalidCredentialsException;
import com.meetingroom.api.repository.AppUserRepository;
import com.meetingroom.api.security.JwtProperties;
import com.meetingroom.api.security.TokenService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final TokenService tokenService;
    private final JwtProperties jwtProperties;

    public AuthService(AuthenticationManager authenticationManager, AppUserRepository userRepository,
                       TokenService tokenService, JwtProperties jwtProperties) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Autentica las credenciales y, si son validas, emite un token.
     *
     * <p>Credenciales incorrectas, usuario inexistente y usuario deshabilitado producen la misma
     * respuesta para no permitir enumerar cuentas.</p>
     */
    public TokenResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException();
        }

        AppUser user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(InvalidCredentialsException::new);

        return new TokenResponse(
                tokenService.issue(user),
                "Bearer",
                jwtProperties.ttl().toSeconds(),
                user.getUsername(),
                user.getRole().name());
    }

}
