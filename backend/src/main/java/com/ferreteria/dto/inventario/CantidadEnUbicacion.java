package com.ferreteria.dto.inventario;

import java.math.BigDecimal;

/**
 * Fila de la consulta agregada de stock: cuanto hay de un producto en una ubicacion.
 */
public record CantidadEnUbicacion(Long productoId, Long ubicacionId, BigDecimal cantidad, BigDecimal stockMinimo) {
}
