package com.ferreteria.dto.comun;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Formato unico de error de toda la API.
 *
 * @param codigo  codigo estable para el frontend (Ej: STOCK_INSUFICIENTE)
 * @param mensaje explicacion para mostrar al usuario
 * @param detalle datos adicionales (Ej: que campo fallo y por que); puede estar vacio
 * @param fecha   momento del error
 */
@Schema(description = "Error devuelto por la API")
public record ErrorResponse(
        @Schema(example = "VALIDACION") String codigo,
        @Schema(example = "Los datos enviados no son validos") String mensaje,
        @Schema(example = "[\"cantidad: debe ser mayor que 0\"]") List<String> detalle,
        OffsetDateTime fecha) {

    public static ErrorResponse de(String codigo, String mensaje, List<String> detalle) {
        return new ErrorResponse(codigo, mensaje, detalle == null ? List.of() : detalle, OffsetDateTime.now());
    }
}
