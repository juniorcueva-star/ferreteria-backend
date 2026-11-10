package com.ferreteria.dto.reposicion;

import com.ferreteria.entity.SolicitudReposicion;
import com.ferreteria.entity.enums.EstadoSolicitud;
import com.ferreteria.entity.enums.UnidadBase;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record SolicitudReposicionResponse(Long id, String codigo, Long ubicacionId, String ubicacionNombre,
                                          EstadoSolicitud estado, String observacion, String usuario,
                                          OffsetDateTime fecha, String atendidaPor, OffsetDateTime atendidaAt,
                                          String motivo, Long trasladoId, String trasladoCodigo,
                                          List<Detalle> detalles) {

    public record Detalle(Long productoId, String productoCodigo, String productoNombre, UnidadBase unidadBase,
                          BigDecimal cantidad) {
    }

    public static SolicitudReposicionResponse desde(SolicitudReposicion s) {
        return new SolicitudReposicionResponse(s.getId(), s.getCodigo(), s.getUbicacion().getId(),
                s.getUbicacion().getNombre(), s.getEstado(), s.getObservacion(), s.getUsuario().getNombres(),
                s.getCreatedAt(), s.getAtendidaPor() == null ? null : s.getAtendidaPor().getNombres(),
                s.getAtendidaAt(), s.getMotivo(), s.getTraslado() == null ? null : s.getTraslado().getId(),
                s.getTraslado() == null ? null : s.getTraslado().getCodigo(),
                s.getDetalles().stream().map(d -> new Detalle(d.getProducto().getId(), d.getProducto().getCodigo(),
                        d.getProducto().getNombre(), d.getProducto().getUnidadBase(), d.getCantidad())).toList());
    }
}
