package com.ferreteria.dto.organizacion;

import com.ferreteria.entity.enums.Rol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioActualizarRequest(
        @NotBlank @Size(max = 120) String nombres,
        @NotNull Rol rol,
        Long ubicacionId,
        @NotNull Boolean activo) {
}
