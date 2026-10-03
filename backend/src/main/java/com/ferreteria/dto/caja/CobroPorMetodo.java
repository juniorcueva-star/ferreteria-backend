package com.ferreteria.dto.caja;

import java.math.BigDecimal;

/**
 * Total cobrado en la caja con un metodo de pago.
 */
public record CobroPorMetodo(String codigo, String nombre, boolean efectivo, BigDecimal monto) {
}
