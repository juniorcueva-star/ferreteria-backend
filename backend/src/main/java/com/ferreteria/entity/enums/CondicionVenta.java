package com.ferreteria.entity.enums;

/**
 * CONTADO se paga completo al vender; CREDITO (fiado) deja saldo pendiente.
 * Debe coincidir con el CHECK ck_venta_condicion.
 */
public enum CondicionVenta {
    CONTADO,
    CREDITO
}
