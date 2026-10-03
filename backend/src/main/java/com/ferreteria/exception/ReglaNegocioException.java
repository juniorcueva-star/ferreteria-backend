package com.ferreteria.exception;

/**
 * La operacion no cumple una regla del negocio (422). Ej: vender sin caja abierta.
 */
public class ReglaNegocioException extends ApiException {

    public ReglaNegocioException(String mensaje) {
        super(CodigoError.REGLA_NEGOCIO, mensaje);
    }
}
