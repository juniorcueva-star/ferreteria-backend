package com.ferreteria.dto.inventario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Correccion manual de stock (merma, rotura, conteo fisico). El motivo es obligatorio.
 *
 * @param ubicacionId el ALMACENERO puede omitirlo (su almacen); el ADMIN debe indicarlo
 */
public record AjusteRequest(
        Long ubicacionId,
        @NotNull TipoAjuste tipo,
        @NotBlank @Size(min = 5, max = 300) String motivo,
        @NotEmpty @Size(max = 200) List<@Valid @NotNull LineaProductoRequest> detalles) {

    public enum TipoAjuste { ENTRADA, SALIDA }
}
