package com.ferreteria.dto.reportes;

import com.ferreteria.entity.enums.UnidadBase;

import java.math.BigDecimal;

/**
 * Producto vendido en el rango de fechas (ventas EMITIDAS).
 *
 * @param cantidadVendida en unidad base (unidades, kilos o metros)
 * @param montoVendido    suma de los importes de las lineas (con IGV, despues de descuentos)
 * @param numeroVentas    en cuantas ventas aparece
 */
public record ProductoVendidoResponse(Long productoId, String productoCodigo, String productoNombre,
                                      UnidadBase unidadBase, BigDecimal cantidadVendida, BigDecimal montoVendido,
                                      long numeroVentas) {
}
