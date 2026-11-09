package com.ferreteria.dto.cotizaciones;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param mensaje texto opcional que se agrega al correo
 */
public record EnvioCorreoRequest(@NotBlank @Email @Size(max = 150) String destinatario, @Size(max = 500) String mensaje) {
}
