package com.ferreteria.service;

import com.ferreteria.exception.ReglaNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Convierte dias calendario de Lima en instantes. Las fechas se guardan con zona horaria (TIMESTAMPTZ);
 * "el dia 5" va desde las 00:00 del 5 hasta las 00:00 del 6 en Lima, sin importar la zona del servidor.
 */
@Component
public class Calendario {

    private final ZoneId zona;

    public Calendario(@Value("${app.zona-horaria}") String zona) {
        this.zona = ZoneId.of(zona);
    }

    public LocalDate hoy() {
        return LocalDate.now(zona);
    }

    /** Inicio del dia (inclusive); null si no hay fecha. */
    public OffsetDateTime inicio(LocalDate fecha) {
        return fecha == null ? null : fecha.atStartOfDay(zona).toOffsetDateTime();
    }

    /** Inicio del dia siguiente (exclusive); null si no hay fecha. */
    public OffsetDateTime finExclusivo(LocalDate fecha) {
        return fecha == null ? null : fecha.plusDays(1).atStartOfDay(zona).toOffsetDateTime();
    }

    public void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReglaNegocioException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
    }
}
