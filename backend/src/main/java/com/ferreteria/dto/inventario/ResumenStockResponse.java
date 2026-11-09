package com.ferreteria.dto.inventario;

import com.ferreteria.entity.enums.TipoUbicacion;
import com.ferreteria.entity.enums.UnidadBase;

import java.math.BigDecimal;
import java.util.List;

/**
 * Disponibilidad de un producto en todas las ubicaciones activas (solo lectura), en unidad base.
 *
 * @param ubicaciones una entrada por cada tienda y almacen activo, con 0 si no hay stock
 * @param total       suma de todas las ubicaciones
 */
public record ResumenStockResponse(Long productoId, String productoCodigo, String productoNombre, UnidadBase unidadBase,
                                   String categoriaNombre, List<StockUbicacion> ubicaciones, BigDecimal total) {

    /**
     * @param bajo true si llego al stock minimo configurado en esa ubicacion
     */
    public record StockUbicacion(Long ubicacionId, String ubicacionNombre, TipoUbicacion tipo, BigDecimal cantidad,
                                 BigDecimal stockMinimo, boolean bajo) {
    }
}
