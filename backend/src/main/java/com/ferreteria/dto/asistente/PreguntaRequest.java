package com.ferreteria.dto.asistente;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Pregunta al asistente con la conversacion anterior del chat (solo texto; el servidor no guarda nada).
 *
 * @param historial mensajes anteriores, del mas antiguo al mas reciente (maximo 20)
 */
public record PreguntaRequest(
        @NotBlank @Size(max = 500) String pregunta,
        @Size(max = 20) List<@Valid @NotNull MensajeChat> historial) {

    /** @param rol USUARIO o ASISTENTE */
    public record MensajeChat(
            @NotNull @Pattern(regexp = "USUARIO|ASISTENTE") String rol,
            @NotBlank @Size(max = 4000) String texto) {
    }
}
