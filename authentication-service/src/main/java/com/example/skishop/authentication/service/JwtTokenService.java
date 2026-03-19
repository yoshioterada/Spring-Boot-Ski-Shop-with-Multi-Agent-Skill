package com.example.skishop.authentication.service;

import com.example.skishop.authentication.config.JwtConfig;
import com.example.skishop.authentication.entity.User;
import com.example.skishop.authentication.exception.InvalidTokenException;
import com.example.skishop.authentication.exception.TokenExpiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtConfig jwtConfig;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, JwtConfig jwtConfig) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.jwtConfig = jwtConfig;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtConfig.getAccessTokenExpiry(), ChronoUnit.SECONDS);

        List<String> roles = user.getRoles().stream().sorted().toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("ski-shop-auth-service")
            .issuedAt(now)
            .expiresAt(expiry)
            .subject(user.getUsername())
            .claim("userId", user.getId().toString())
            .claim("email", user.getEmail())
            .claim("roles", roles)
            .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Jwt validateAndDecodeToken(String token) {
        try {
            return jwtDecoder.decode(token);
        } catch (JwtValidationException ex) {
            boolean isExpired = ex.getMessage() != null && ex.getMessage().contains("expired");
            if (isExpired) {
                log.debug("JWT が期限切れです");
                throw new TokenExpiredException();
            }
            log.debug("JWT の検証に失敗しました: {}", ex.getMessage());
            throw new InvalidTokenException();
        } catch (JwtException ex) {
            log.debug("JWT の検証に失敗しました: {}", ex.getMessage());
            throw new InvalidTokenException();
        }
    }

    public String extractUsername(String token) {
        return validateAndDecodeToken(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            validateAndDecodeToken(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public long getAccessTokenExpirySeconds() {
        return jwtConfig.getAccessTokenExpiry();
    }
}
