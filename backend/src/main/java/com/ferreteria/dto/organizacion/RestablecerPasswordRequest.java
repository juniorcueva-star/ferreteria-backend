package com.ferreteria.dto.organizacion;

import com.ferreteria.dto.comun.Contrasena;
import jakarta.validation.constraints.NotBlank;

public record RestablecerPasswordRequest(
        @NotBlank @Contrasena String passwordNueva) {
}
