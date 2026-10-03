package com.ferreteria.exception;

/**
 * El registro pedido no existe (404).
 */
public class RecursoNoEncontradoException extends ApiException {

    public RecursoNoEncontradoException(String recurso, Object id) {
        super(CodigoError.RECURSO_NO_ENCONTRADO, recurso + " no encontrado(a) con id " + id);
    }

    public RecursoNoEncontradoException(String mensaje) {
        super(CodigoError.RECURSO_NO_ENCONTRADO, mensaje);
    }
}
