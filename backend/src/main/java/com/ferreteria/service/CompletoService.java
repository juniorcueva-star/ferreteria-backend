package com.ferreteria.service;

import com.ferreteria.dto.catalogo.CompletoRequest;
import com.ferreteria.dto.catalogo.CompletoResponse;
import com.ferreteria.entity.Completo;
import com.ferreteria.entity.CompletoComponente;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.EntidadAuditada;
import com.ferreteria.exception.DuplicadoException;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.CompletoRepository;
import com.ferreteria.repository.PresentacionRepository;
import com.ferreteria.repository.ProductoRepository;
import com.ferreteria.util.Montos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Completos de un producto (pedido del dueno): el producto junto con otras piezas, a precio propio. Solo el ADMIN
 * los crea o edita; no se borran (se desactivan) porque las ventas y cotizaciones los referencian.
 */
@Service
@RequiredArgsConstructor
public class CompletoService {

    private final CompletoRepository completoRepository;
    private final ProductoRepository productoRepository;
    private final PresentacionRepository presentacionRepository;
    private final AuditoriaService auditoria;

    @Transactional
    public CompletoResponse crear(Long productoId, CompletoRequest request) {
        Producto producto = buscarProducto(productoId);
        String nombre = request.nombre().trim();
        if (completoRepository.existsByProductoIdAndNombreIgnoreCase(productoId, nombre)) {
            throw new DuplicadoException(producto.getNombre() + " ya tiene un completo llamado " + nombre);
        }
        Completo completo = new Completo();
        completo.setProducto(producto);
        copiarDatos(completo, request);
        completoRepository.save(completo);
        auditoria.registrar(EntidadAuditada.PRODUCTO, producto.getId(), "COMPLETO", producto.getCodigo() + " "
                + producto.getNombre() + ": creó el completo " + completo.getNombre() + " ("
                + completo.composicion() + ") a " + AuditoriaService.soles(completo.getPrecioVenta()));
        return CompletoResponse.desde(completo);
    }

    @Transactional
    public CompletoResponse actualizar(Long productoId, Long completoId, CompletoRequest request) {
        Completo completo = completoRepository.findById(completoId)
                .filter(c -> c.getProducto().getId().equals(productoId))
                .orElseThrow(() -> new RecursoNoEncontradoException("Completo", completoId));
        String nombre = request.nombre().trim();
        if (completoRepository.existsByProductoIdAndNombreIgnoreCaseAndIdNot(productoId, nombre, completoId)) {
            throw new DuplicadoException(completo.getProducto().getNombre() + " ya tiene un completo llamado " + nombre);
        }
        String composicionAntes = completo.composicion();
        BigDecimal precioAntes = completo.getPrecioVenta();
        boolean activoAntes = completo.isActivo();
        String nombreAntes = completo.getNombre();
        completo.getComponentes().clear();
        completoRepository.flush(); // borra las piezas antes de volver a crearlas (UNIQUE completo + presentacion)
        copiarDatos(completo, request);
        AuditoriaService.Cambios cambios = new AuditoriaService.Cambios()
                .campo("Nombre", nombreAntes, completo.getNombre())
                .campo("Precio", AuditoriaService.soles(precioAntes), AuditoriaService.soles(completo.getPrecioVenta()))
                .campo("Activo", activoAntes, completo.isActivo())
                .campo("Piezas", composicionAntes, completo.composicion());
        if (cambios.hay()) {
            auditoria.registrar(EntidadAuditada.PRODUCTO, productoId, "COMPLETO", completo.getProducto().getCodigo()
                    + " " + completo.getProducto().getNombre() + ", completo " + completo.getNombre() + ": " + cambios);
        }
        return CompletoResponse.desde(completo);
    }

    private void copiarDatos(Completo completo, CompletoRequest request) {
        Set<Long> ids = new HashSet<>();
        for (CompletoRequest.Componente c : request.componentes()) {
            if (!ids.add(c.presentacionId())) {
                throw new ReglaNegocioException("Una pieza esta repetida: sume la cantidad en una sola linea");
            }
        }
        Map<Long, Presentacion> presentaciones = presentacionRepository.findByIdIn(List.copyOf(ids)).stream()
                .collect(Collectors.toMap(Presentacion::getId, Function.identity()));
        completo.setNombre(request.nombre().trim());
        completo.setActivo(request.activo() == null || request.activo());
        for (CompletoRequest.Componente c : request.componentes()) {
            Presentacion presentacion = presentaciones.get(c.presentacionId());
            if (presentacion == null) {
                throw new RecursoNoEncontradoException("Presentacion", c.presentacionId());
            }
            if (!presentacion.isActivo() || !presentacion.getProducto().isActivo()) {
                throw new ReglaNegocioException(presentacion.getProducto().getNombre() + " ("
                        + presentacion.getNombre() + ") esta inactivo: no puede ser pieza de un completo");
            }
            ReglaDecimales.validar(c.cantidad(), presentacion, presentacion.getProducto());
            CompletoComponente componente = new CompletoComponente();
            componente.setProducto(presentacion.getProducto());
            componente.setPresentacion(presentacion);
            componente.setCantidad(Montos.cantidad(c.cantidad()));
            completo.agregarComponente(componente);
        }
        completo.setPrecioVenta(request.precioVenta() != null ? Montos.dinero(request.precioVenta())
                : CompletoResponse.precioSugerido(completo));
        if (completo.composicion().length() > 300) {
            throw new ReglaNegocioException("El completo tiene demasiadas piezas para describirlo: use menos piezas");
        }
    }

    private Producto buscarProducto(Long id) {
        return productoRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Producto", id));
    }
}
