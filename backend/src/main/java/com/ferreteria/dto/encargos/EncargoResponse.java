package com.ferreteria.dto.encargos;

import com.ferreteria.entity.Encargo;
import com.ferreteria.entity.enums.EstadoEncargo;
import com.ferreteria.util.Montos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * Encargo con su total y el recargo informativo sobre el precio de lista.
 *
 * @param precioLista       precio actual de la presentacion
 * @param recargoPorcentaje (precio acordado / precio de lista - 1) x 100; null si el precio de lista es 0
 * @param ventaId           venta creada al entregar (null mientras esta pendiente)
 */
public record EncargoResponse(Long id, String codigo, Long ubicacionId, String ubicacionNombre, Long clienteId,
                              String clienteNombre, Long productoId, String productoCodigo, String productoNombre,
                              Long presentacionId, String presentacionNombre, BigDecimal cantidad,
                              BigDecimal precioUnitario, BigDecimal total, BigDecimal precioLista,
                              BigDecimal recargoPorcentaje, BigDecimal costoReferencia, String observacion,
                              EstadoEncargo estado, Long ventaId, String ventaNumero, String usuario,
                              OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    private static final BigDecimal CIEN = new BigDecimal("100");

    public static EncargoResponse desde(Encargo e) {
        BigDecimal lista = e.getPresentacion().getPrecioVenta();
        BigDecimal recargo = lista.signum() == 0 ? null
                : e.getPrecioUnitario().subtract(lista).multiply(CIEN).divide(lista, 2, RoundingMode.HALF_UP);
        return new EncargoResponse(e.getId(), e.getCodigo(), e.getUbicacion().getId(), e.getUbicacion().getNombre(),
                e.getCliente() == null ? null : e.getCliente().getId(),
                e.getCliente() == null ? null : e.getCliente().getNombre(),
                e.getProducto().getId(), e.getProducto().getCodigo(), e.getProducto().getNombre(),
                e.getPresentacion().getId(), e.getPresentacion().getNombre(), e.getCantidad(), e.getPrecioUnitario(),
                Montos.dinero(e.getCantidad().multiply(e.getPrecioUnitario())), lista, recargo,
                e.getCostoReferencia(), e.getObservacion(), e.getEstado(),
                e.getVenta() == null ? null : e.getVenta().getId(),
                e.getVenta() == null ? null : e.getVenta().getNumeroDocumento(),
                e.getUsuario().getUsername(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
