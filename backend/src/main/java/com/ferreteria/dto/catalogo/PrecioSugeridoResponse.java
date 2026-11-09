package com.ferreteria.dto.catalogo;

import java.math.BigDecimal;

/**
 * Datos para decidir el precio de una presentacion.
 *
 * @param costoReferencia costo de la ultima compra por unidad base x factor (null si nunca se compro)
 * @param precioSugerido  redondear_2(costo x (1 + ganancia / 100) + adicional); null sin costo
 * @param precioVenta     precio final vigente (no cambia hasta que el usuario lo confirma)
 */
public record PrecioSugeridoResponse(Long presentacionId, String presentacionNombre, BigDecimal costoReferencia,
                                     BigDecimal porcentajeGanancia, BigDecimal montoAdicional,
                                     BigDecimal precioSugerido, BigDecimal precioVenta) {
}
