package com.ferreteria.service.stock;

import com.ferreteria.entity.Producto;

import java.math.BigDecimal;

/**
 * Cantidad (en unidad base, siempre positiva) de un producto que entra o sale.
 *
 * @param costoUnitario costo por unidad base; solo se informa en compras (puede ser null)
 */
public record LineaMovimiento(Producto producto, BigDecimal cantidadBase, BigDecimal costoUnitario) {

    public static LineaMovimiento de(Producto producto, BigDecimal cantidadBase) {
        return new LineaMovimiento(producto, cantidadBase, null);
    }
}
