package com.ferreteria.dto.organizacion;

import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.enums.Rol;

/**
 * Datos publicos de un usuario (nunca incluye el hash de la contrasena).
 */
public record UsuarioResponse(Long id, String nombres, String username, Rol rol, Long ubicacionId,
                              String ubicacionNombre, boolean activo) {

    public static UsuarioResponse desde(Usuario u) {
        Ubicacion ubicacion = u.getUbicacion();
        return new UsuarioResponse(u.getId(), u.getNombres(), u.getUsername(), u.getRol(),
                ubicacion == null ? null : ubicacion.getId(),
                ubicacion == null ? null : ubicacion.getNombre(),
                u.isActivo());
    }
}
