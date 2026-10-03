package com.ferreteria.dto.compras;

import com.ferreteria.entity.CompraDetalle;
import com.ferreteria.entity.Presentacion;

import java.math.BigDecimal;

public record CompraDetalleResponse(Long id, Long productoId, String productoNombre, Long presentacionId,
                                    String presentacionNombre, BigDecimal cantidad, BigDecimal cantidadBase,
                                    BigDecimal costoUnitario, BigDecimal subtotal) {

    public static CompraDetalleResponse desde(CompraDetalle d) {
        Presentacion presentacion = d.getPresentacion();
        return new CompraDetalleResponse(d.getId(), d.getProducto().getId(), d.getProducto().getNombre(),
                presentacion == null ? null : presentacion.getId(),
                presentacion == null ? null : presentacion.getNombre(),
                d.getCantidad(), d.getCantidadBase(), d.getCostoUnitario(), d.getSubtotal());
    }
}
