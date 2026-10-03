package com.ferreteria.exception;

import java.util.List;

/**
 * Error de negocio previsto. El manejador global lo convierte en un ErrorResponse con su codigo.
 */
public class ApiException extends RuntimeException {

    private final CodigoError codigo;
    private final List<String> detalle;

    public ApiException(CodigoError codigo, String mensaje) {
        this(codigo, mensaje, List.of());
    }

    public ApiException(CodigoError codigo, String mensaje, List<String> detalle) {
        super(mensaje);
        this.codigo = codigo;
        this.detalle = List.copyOf(detalle);
    }

    public CodigoError getCodigo() {
        return codigo;
    }

    public List<String> getDetalle() {
        return detalle;
    }
}
