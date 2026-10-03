package com.ferreteria.dto.organizacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmpresaRequest(
        @NotBlank @Pattern(regexp = "\\d{11}", message = "debe tener 11 digitos") String ruc,
        @NotBlank @Size(max = 150) String razonSocial,
        @Size(max = 150) String nombreComercial,
        @Size(max = 200) String direccion,
        Boolean activo) {
}
