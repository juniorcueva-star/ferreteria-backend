package com.ferreteria.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracion del token JWT (app.jwt.* en application.yml).
 *
 * @param secreto           clave HMAC-SHA256; viene de la variable JWT_SECRET (minimo 32 caracteres)
 * @param expiracionMinutos duracion del token; por defecto un turno de trabajo (8 horas)
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "Configure la variable de entorno JWT_SECRET")
        @Size(min = 32, message = "JWT_SECRET debe tener al menos 32 caracteres")
        String secreto,
        @Min(1) long expiracionMinutos) {
}
