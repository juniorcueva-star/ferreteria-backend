package com.ferreteria.entity.enums;

/**
 * Estado de un traslado: ENVIADO (salio del origen), RECIBIDO (entro al destino) o ANULADO.
 * Debe coincidir con el CHECK ck_traslado_estado.
 */
public enum EstadoTraslado {
    ENVIADO,
    RECIBIDO,
    ANULADO
}
