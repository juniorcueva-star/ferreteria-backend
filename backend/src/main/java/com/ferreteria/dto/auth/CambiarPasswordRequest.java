package com.ferreteria.dto.auth;

import com.ferreteria.dto.comun.Contrasena;
import jakarta.validation.constraints.NotBlank;

public record CambiarPasswordRequest(
        @NotBlank String passwordActual,
        @NotBlank @Contrasena String passwordNueva) {
}
