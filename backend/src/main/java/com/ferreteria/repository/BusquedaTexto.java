package com.ferreteria.repository;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Normaliza consultas de catalogo sin depender de extensiones de PostgreSQL. */
public final class BusquedaTexto {

    private BusquedaTexto() {
    }

    public static List<String> tokens(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        return List.of(normalizar(texto).split("\\s+"));
    }

    public static String normalizar(String texto) {
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
