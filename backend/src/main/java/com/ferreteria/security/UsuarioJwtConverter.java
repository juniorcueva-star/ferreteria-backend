package com.ferreteria.security;

import com.ferreteria.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Convierte un JWT valido en el usuario autenticado. Lee el usuario de la BD en cada peticion:
 * asi un usuario desactivado pierde el acceso al instante y un cambio de rol o tienda aplica de inmediato.
 */
@Component
@RequiredArgsConstructor
public class UsuarioJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UsuarioRepository usuarioRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Number id = jwt.getClaim(JwtService.CLAIM_USUARIO_ID);
        if (id == null) {
            throw new DisabledException("Token sin usuario");
        }
        UsuarioActual usuario = usuarioRepository.buscarUsuarioActivo(id.longValue())
                .orElseThrow(() -> new DisabledException("Usuario inactivo o inexistente"));
        return new UsuarioAuthenticationToken(usuario);
    }
}
