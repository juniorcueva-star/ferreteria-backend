package com.ferreteria.dto.compras;

import com.ferreteria.entity.Compra;
import com.ferreteria.entity.enums.EstadoCompra;
import com.ferreteria.entity.enums.TipoComprobanteCompra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Compra con su detalle. En los listados el detalle va vacio para no cargar de mas.
 */
public record CompraResponse(Long id, Long empresaId, String empresaRazonSocial, Long proveedorId,
                             String proveedorRazonSocial, Long ubicacionId, String ubicacionNombre,
                             String usuario, TipoComprobanteCompra tipoComprobante, String serieNumero,
                             LocalDate fechaEmision, BigDecimal subtotal, BigDecimal igv, BigDecimal total,
                             EstadoCompra estado, String observacion, OffsetDateTime createdAt,
                             List<CompraDetalleResponse> detalles) {

    public static CompraResponse resumen(Compra c) {
        return crear(c, List.of());
    }

    public static CompraResponse completa(Compra c) {
        return crear(c, c.getDetalles().stream().map(CompraDetalleResponse::desde).toList());
    }

    private static CompraResponse crear(Compra c, List<CompraDetalleResponse> detalles) {
        return new CompraResponse(c.getId(), c.getEmpresa().getId(), c.getEmpresa().getRazonSocial(),
                c.getProveedor().getId(), c.getProveedor().getRazonSocial(), c.getUbicacion().getId(),
                c.getUbicacion().getNombre(), c.getUsuario().getUsername(), c.getTipoComprobante(),
                c.getSerieNumero(), c.getFechaEmision(), c.getSubtotal(), c.getIgv(), c.getTotal(), c.getEstado(),
                c.getObservacion(), c.getCreatedAt(), detalles);
    }
}
