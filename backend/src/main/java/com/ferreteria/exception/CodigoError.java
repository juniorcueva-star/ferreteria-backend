package com.ferreteria.exception;

import org.springframework.http.HttpStatus;

/**
 * Codigos de error de la API y el estado HTTP con el que se responden.
 */
public enum CodigoError {
    VALIDACION(HttpStatus.BAD_REQUEST),
    SOLICITUD_INVALIDA(HttpStatus.BAD_REQUEST),
    NO_AUTENTICADO(HttpStatus.UNAUTHORIZED),
    CREDENCIALES_INVALIDAS(HttpStatus.UNAUTHORIZED),
    ACCESO_DENEGADO(HttpStatus.FORBIDDEN),
    RECURSO_NO_ENCONTRADO(HttpStatus.NOT_FOUND),
    METODO_NO_PERMITIDO(HttpStatus.METHOD_NOT_ALLOWED),
    DUPLICADO(HttpStatus.CONFLICT),
    STOCK_INSUFICIENTE(HttpStatus.CONFLICT),
    CONFLICTO_DATOS(HttpStatus.CONFLICT),
    ARCHIVO_DEMASIADO_GRANDE(HttpStatus.CONTENT_TOO_LARGE),
    TIPO_ARCHIVO_NO_SOPORTADO(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    REGLA_NEGOCIO(HttpStatus.UNPROCESSABLE_CONTENT),
    SERVICIO_EXTERNO(HttpStatus.BAD_GATEWAY),
    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    CodigoError(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
