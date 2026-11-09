package com.ferreteria.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reglas de busqueda de catalogo: texto sin acentos, palabras de la consulta,
 * indice por palabras y abreviaturas (per = perno, hex = hexagonal).
 */
public final class BusquedaTexto {

    private BusquedaTexto() {
    }

    /** Minusculas y sin acentos (Ñ queda como N). */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /** Palabras de la consulta. "Per  HEX zin" -> [per, hex, zin]. */
    public static List<String> tokens(String texto) {
        String normalizado = normalizar(texto);
        if (normalizado.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(normalizado.split("\\s+"));
    }

    /**
     * Texto que se guarda para buscar: cada parte normalizada y rodeada de espacios.
     * Las partes vacias se omiten. Ej: indice("PERNO hexagonal", "1/4 X 1/2") -> " perno hexagonal 1/4 x 1/2 ".
     */
    public static String indice(String... partes) {
        StringBuilder indice = new StringBuilder(" ");
        for (String parte : partes) {
            String normalizada = normalizar(parte);
            if (!normalizada.isEmpty()) {
                indice.append(normalizada).append(' ');
            }
        }
        return indice.toString();
    }

    /** Palabra y, si es abreviatura conocida, su termino completo. "hex" -> [hex, hexagonal]. */
    public static List<String> alternativas(String palabra, Map<String, String> sinonimos) {
        List<String> alternativas = new ArrayList<>();
        alternativas.add(palabra);
        String termino = sinonimos.get(palabra);
        if (termino != null && !termino.equals(palabra)) {
            alternativas.add(termino);
        }
        return alternativas;
    }

    /** Patron LIKE: la palabra aparece al inicio de alguna palabra del indice. */
    public static String patronInicioPalabra(String palabra) {
        return "% " + palabra.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
