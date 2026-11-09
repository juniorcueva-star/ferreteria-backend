package com.ferreteria.entity.enums;

/**
 * Estado de un encargo. Debe coincidir con el CHECK ck_encargo_estado.
 * PENDIENTE: registrado, aun sin stock; ENTREGADO: se creo la venta; ANULADO: el cliente desistio.
 */
public enum EstadoEncargo {
    PENDIENTE,
    ENTREGADO,
    ANULADO
}
