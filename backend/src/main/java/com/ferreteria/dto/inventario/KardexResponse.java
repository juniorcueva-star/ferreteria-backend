package com.ferreteria.dto.inventario;

import com.ferreteria.entity.MovimientoInventario;
import com.ferreteria.entity.enums.TipoMovimiento;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Fila del kardex.
 *
 * @param cantidad  positiva = entro, negativa = salio (unidad base)
 * @param documento documento que lo origino (Ej: "COMPRA 15", "VENTA NV01-000003", "TR-000002")
 */
public record KardexResponse(Long id, OffsetDateTime fecha, Long productoId, String productoNombre,
                             Long ubicacionId, String ubicacionNombre, TipoMovimiento tipo, BigDecimal cantidad,
                             BigDecimal saldoResultante, BigDecimal costoUnitario, String documento, String usuario,
                             String motivo) {

    public static KardexResponse desde(MovimientoInventario m) {
        return new KardexResponse(m.getId(), m.getFecha(), m.getProducto().getId(), m.getProducto().getNombre(),
                m.getUbicacion().getId(), m.getUbicacion().getNombre(), m.getTipo(), m.getCantidad(),
                m.getSaldoResultante(), m.getCostoUnitario(), documento(m), m.getUsuario().getUsername(),
                m.getMotivo());
    }

    private static String documento(MovimientoInventario m) {
        if (m.getCompra() != null) {
            return "COMPRA " + m.getCompra().getId();
        }
        if (m.getVenta() != null) {
            return "VENTA " + m.getVenta().getNumeroDocumento();
        }
        if (m.getTraslado() != null) {
            return m.getTraslado().getCodigo();
        }
        return null;
    }
}
