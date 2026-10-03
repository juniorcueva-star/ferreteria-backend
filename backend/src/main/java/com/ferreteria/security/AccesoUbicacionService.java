package com.ferreteria.security;

import com.ferreteria.exception.AccesoDenegadoException;
import com.ferreteria.exception.ReglaNegocioException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Seguridad por tienda: el ADMIN ve y opera todas las ubicaciones; cualquier otro usuario
 * solo la suya (vendedor = su tienda, almacenero = su almacen).
 * Los services lo usan antes de leer o modificar datos que pertenecen a una ubicacion.
 */
@Service
public class AccesoUbicacionService {

    private static final String SIN_ACCESO = "No tiene acceso a datos de otra tienda o almacen";

    public UsuarioActual usuarioActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UsuarioActual usuario)) {
            throw new AccesoDenegadoException("No hay un usuario autenticado");
        }
        return usuario;
    }

    /** Falla con 403 si el usuario no es ADMIN y la ubicacion no es la suya. */
    public void validarAcceso(Long ubicacionId) {
        UsuarioActual usuario = usuarioActual();
        if (!usuario.esAdmin() && !Objects.equals(usuario.ubicacionId(), ubicacionId)) {
            throw new AccesoDenegadoException(SIN_ACCESO);
        }
    }

    /**
     * Ubicacion para filtrar un listado. El ADMIN puede pedir cualquiera o ninguna (null = todas).
     * Los demas siempre ven solo la suya; si piden otra se responde 403.
     */
    public Long ubicacionParaConsultar(Long solicitada) {
        UsuarioActual usuario = usuarioActual();
        if (usuario.esAdmin()) {
            return solicitada;
        }
        if (solicitada != null && !solicitada.equals(usuario.ubicacionId())) {
            throw new AccesoDenegadoException(SIN_ACCESO);
        }
        return usuario.ubicacionId();
    }

    /**
     * Ubicacion donde se va a operar (Ej: abrir caja, registrar compra). El ADMIN debe indicarla;
     * los demas operan en la suya y no pueden indicar otra.
     */
    public Long ubicacionParaOperar(Long solicitada) {
        UsuarioActual usuario = usuarioActual();
        if (usuario.esAdmin()) {
            if (solicitada == null) {
                throw new ReglaNegocioException("Debe indicar la ubicacion (ubicacionId)");
            }
            return solicitada;
        }
        if (solicitada != null && !solicitada.equals(usuario.ubicacionId())) {
            throw new AccesoDenegadoException(SIN_ACCESO);
        }
        return usuario.ubicacionId();
    }
}
