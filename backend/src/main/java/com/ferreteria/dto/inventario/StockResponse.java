package com.ferreteria.dto.inventario;

import com.ferreteria.entity.Stock;
import com.ferreteria.entity.enums.UnidadBase;

import java.math.BigDecimal;

/**
 * Stock de un producto en una ubicacion (en unidad base).
 *
 * @param bajo true si la cantidad llego al minimo configurado
 */
public record StockResponse(Long productoId, String productoCodigo, String productoNombre, UnidadBase unidadBase,
                            Long ubicacionId, String ubicacionNombre, BigDecimal cantidad, BigDecimal stockMinimo,
                            boolean bajo) {

    public static StockResponse desde(Stock s) {
        boolean bajo = s.getStockMinimo().signum() > 0 && s.getCantidad().compareTo(s.getStockMinimo()) <= 0;
        return new StockResponse(s.getProducto().getId(), s.getProducto().getCodigo(), s.getProducto().getNombre(),
                s.getProducto().getUnidadBase(), s.getUbicacion().getId(), s.getUbicacion().getNombre(),
                s.getCantidad(), s.getStockMinimo(), bajo);
    }
}
