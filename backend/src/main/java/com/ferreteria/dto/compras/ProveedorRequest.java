package com.ferreteria.dto.compras;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProveedorRequest(
        @Pattern(regexp = "\\d{11}", message = "debe tener 11 digitos") String ruc,
        @NotBlank @Size(max = 150) String razonSocial,
        @Size(max = 100) String contacto,
        @Size(max = 20) String telefono,
        @Email @Size(max = 100) String email,
        @Size(max = 200) String direccion,
        Boolean activo) {
}
