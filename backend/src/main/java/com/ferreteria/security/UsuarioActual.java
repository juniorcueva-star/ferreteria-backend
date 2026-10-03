package com.ferreteria.security;

import com.ferreteria.entity.enums.Rol;

/**
 * Datos del usuario que hace la peticion, leidos de la BD en cada request a partir del token.
 *
 * @param ubicacionId tienda o almacen donde trabaja; null solo para el ADMIN
 */
public record UsuarioActual(Long id, String username, String nombres, Rol rol, Long ubicacionId) {

    public boolean esAdmin() {
        return rol == Rol.ADMIN;
    }
}
