package com.ferreteria.entity.enums;

/**
 * Estado de una cotizacion. Debe coincidir con el CHECK ck_cotizacion_estado.
 * BORRADOR: se puede editar; EMITIDA: ya se entrego al cliente; ANULADA: no vale (si no se convirtio en venta).
 */
public enum EstadoCotizacion {
    BORRADOR,
    EMITIDA,
    ANULADA
}
