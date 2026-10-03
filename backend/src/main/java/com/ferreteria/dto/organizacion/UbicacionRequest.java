package com.ferreteria.dto.organizacion;

import com.ferreteria.entity.enums.TipoUbicacion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Una TIENDA necesita empresaId (su RUC); el ALMACEN no lleva empresa.
 * El tipo no se puede cambiar despues de crear la ubicacion.
 */
public record UbicacionRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotNull TipoUbicacion tipo,
        Long empresaId,
        @Size(max = 200) String direccion,
        Boolean activo) {
}
