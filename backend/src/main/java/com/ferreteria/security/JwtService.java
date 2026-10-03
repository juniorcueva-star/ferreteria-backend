package com.ferreteria.security;

import com.ferreteria.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Genera los tokens JWT firmados con HS256. La validacion la hace Spring Security (resource server).
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    /** Claim con el id del usuario. */
    public static final String CLAIM_USUARIO_ID = "uid";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public TokenGenerado generar(Usuario usuario) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(jwtProperties.expiracionMinutos(), ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ferreteria-backend")
                .subject(usuario.getUsername())
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim(CLAIM_USUARIO_ID, usuario.getId())
                .claim("rol", usuario.getRol().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenGenerado(token, expira);
    }

    public record TokenGenerado(String token, Instant expiraEn) {
    }
}
