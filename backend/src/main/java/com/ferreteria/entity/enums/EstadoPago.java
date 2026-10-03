package com.ferreteria.entity.enums;

/**
 * Estado de un pago. Los pagos de una venta anulada pasan a ANULADO.
 * Debe coincidir con el CHECK ck_pago_estado.
 */
public enum EstadoPago {
    VALIDO,
    ANULADO
}
