package com.ferreteria.exception;

/**
 * Ya existe un registro con ese dato unico (409). Ej: RUC o codigo de producto repetido.
 */
public class DuplicadoException extends ApiException {

    public DuplicadoException(String mensaje) {
        super(CodigoError.DUPLICADO, mensaje);
    }
}
