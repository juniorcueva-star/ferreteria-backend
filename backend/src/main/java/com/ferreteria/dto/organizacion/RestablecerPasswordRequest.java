package com.ferreteria.dto.organizacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RestablecerPasswordRequest(
        @NotBlank @Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres") String passwordNueva) {
}
