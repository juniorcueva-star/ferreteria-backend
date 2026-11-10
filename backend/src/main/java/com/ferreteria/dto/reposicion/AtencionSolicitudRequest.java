package com.ferreteria.dto.reposicion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Atender un pedido de reposicion con un traslado.
 *
 * @param origenId almacen que envia; el ALMACENERO lo omite (su almacen), el ADMIN lo indica
 * @param detalles cantidades a enviar por producto (unidad base); 0 = no se envia. Sin detalles se envia lo pedido
 */
public record AtencionSolicitudRequest(
        Long origenId,
        @Size(max = 300) String observacion,
        @Size(max = 100) List<@Valid @NotNull Linea> detalles) {

    public record Linea(
            @NotNull Long productoId,
            @NotNull @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 11, fraction = 3) BigDecimal cantidad) {
    }
}
