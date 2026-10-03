package com.ferreteria.service;

import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.Usuario;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.EmpresaRepository;
import com.ferreteria.repository.PresentacionRepository;
import com.ferreteria.repository.ProductoRepository;
import com.ferreteria.repository.UbicacionRepository;
import com.ferreteria.repository.UsuarioRepository;
import com.ferreteria.security.AccesoUbicacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Busquedas que repiten varios services (compras, traslados, ventas): obtiene la entidad y valida que exista
 * y que este activa. Es de uso interno entre services; nunca se expone a los controllers.
 */
@Component
@RequiredArgsConstructor
public class Buscador {

    private final ProductoRepository productoRepository;
    private final PresentacionRepository presentacionRepository;
    private final UbicacionRepository ubicacionRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AccesoUbicacionService accesoUbicacion;

    public Producto productoActivo(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto", id));
        if (!producto.isActivo()) {
            throw new ReglaNegocioException("El producto " + producto.getNombre() + " esta inactivo");
        }
        return producto;
    }

    /** La presentacion debe existir, estar activa y ser del producto indicado. */
    public Presentacion presentacionActiva(Long presentacionId, Producto producto) {
        Presentacion presentacion = presentacionRepository.findById(presentacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Presentacion", presentacionId));
        if (!presentacion.getProducto().getId().equals(producto.getId())) {
            throw new ReglaNegocioException("La presentacion " + presentacionId + " no pertenece al producto "
                    + producto.getNombre());
        }
        if (!presentacion.isActivo()) {
            throw new ReglaNegocioException("La presentacion " + presentacion.getNombre() + " de "
                    + producto.getNombre() + " esta inactiva");
        }
        return presentacion;
    }

    public Ubicacion ubicacionActiva(Long id) {
        Ubicacion ubicacion = ubicacionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ubicacion", id));
        if (!ubicacion.isActivo()) {
            throw new ReglaNegocioException("La ubicacion " + ubicacion.getNombre() + " esta inactiva");
        }
        return ubicacion;
    }

    public Empresa empresaActiva(Long id) {
        Empresa empresa = empresaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa", id));
        if (!empresa.isActivo()) {
            throw new ReglaNegocioException("La empresa " + empresa.getRazonSocial() + " esta inactiva");
        }
        return empresa;
    }

    /** Usuario que hace la peticion, como referencia para guardar en los documentos (no consulta la BD). */
    public Usuario usuarioActual() {
        return usuarioRepository.getReferenceById(accesoUbicacion.usuarioActual().id());
    }
}
