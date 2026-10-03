package com.ferreteria.exception;

/**
 * El usuario esta autenticado pero no puede ver u operar ese dato (403).
 * Ej: un vendedor que intenta ver ventas de otra tienda.
 */
public class AccesoDenegadoException extends ApiException {

    public AccesoDenegadoException(String mensaje) {
        super(CodigoError.ACCESO_DENEGADO, mensaje);
    }
}
