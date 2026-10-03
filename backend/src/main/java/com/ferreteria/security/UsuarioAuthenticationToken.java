package com.ferreteria.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Autenticacion de Spring Security cuyo "principal" es el UsuarioActual.
 * Su autoridad es ROLE_ + rol, que es lo que revisa hasRole(...) en la tabla de permisos de SecurityConfig.
 */
public class UsuarioAuthenticationToken extends AbstractAuthenticationToken {

    private final UsuarioActual usuario;

    public UsuarioAuthenticationToken(UsuarioActual usuario) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol().name())));
        this.usuario = usuario;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public UsuarioActual getPrincipal() {
        return usuario;
    }

    @Override
    public String getName() {
        return usuario.username();
    }
}
