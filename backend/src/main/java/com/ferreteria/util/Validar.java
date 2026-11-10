package com.ferreteria.util;

import com.ferreteria.exception.ReglaNegocioException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Reglas de formato de los datos que se escriben a mano (pedido del dueno): telefonos de 9 digitos que empiezan con
 * 9, nombres de personas solo con letras, nombres que tengan al menos una letra y RUC peruanos validos.
 * Devuelven el texto limpio (sin espacios de mas) o null si viene vacio; si no cumple, responden 422 con un mensaje
 * claro.
 */
public final class Validar {

    private static final String LETRAS = "\\p{L}";

    private Validar() {
    }

    /** Celular peruano: 9 digitos que empiezan con 9 (se aceptan espacios o guiones al escribirlo). Opcional. */
    public static String telefono(String texto, String campo) {
        String limpio = vacioANull(texto);
        if (limpio == null) {
            return null;
        }
        String digitos = limpio.replaceAll("[\\s-]", "");
        if (!digitos.matches("9\\d{8}")) {
            throw new ReglaNegocioException(campo + ": el celular debe tener 9 digitos y empezar con 9 (Ej: 987654321)");
        }
        return digitos;
    }

    /** Uno o mas celulares separados por "/" (Ej: "950 104 503 / 947 319 013"); se guardan con formato. */
    public static String telefonos(String texto, String campo) {
        String limpio = vacioANull(texto);
        if (limpio == null) {
            return null;
        }
        return Arrays.stream(limpio.split("/"))
                .map(parte -> telefono(parte, campo))
                .map(t -> t.substring(0, 3) + " " + t.substring(3, 6) + " " + t.substring(6))
                .collect(Collectors.joining(" / "));
    }

    /** Nombre de una persona: solo letras (con tildes y n), espacios, punto, apostrofo o guion. */
    public static String nombrePersona(String texto, String campo, boolean obligatorio) {
        String limpio = vacioANull(texto);
        if (limpio == null) {
            if (obligatorio) {
                throw new ReglaNegocioException(campo + " es obligatorio");
            }
            return null;
        }
        if (!limpio.matches(LETRAS + "[" + LETRAS + " .'-]*")) {
            throw new ReglaNegocioException(campo + ": solo letras (sin numeros ni simbolos)");
        }
        return limpio.replaceAll("\\s+", " ");
    }

    /** Nombres de empresas, productos, tiendas o categorias: pueden llevar numeros, pero no solo numeros. */
    public static String conLetras(String texto, String campo) {
        String limpio = vacioANull(texto);
        if (limpio == null) {
            return null;
        }
        if (!limpio.matches(".*" + LETRAS + ".*")) {
            throw new ReglaNegocioException(campo + ": debe tener letras, no solo numeros o simbolos");
        }
        return limpio.replaceAll("\\s+", " ");
    }

    /** RUC peruano: 11 digitos que empiezan con 10, 15, 16, 17 o 20. */
    public static String ruc(String texto, boolean obligatorio) {
        String limpio = vacioANull(texto);
        if (limpio == null) {
            if (obligatorio) {
                throw new ReglaNegocioException("El RUC es obligatorio");
            }
            return null;
        }
        if (!limpio.matches("(10|15|16|17|20)\\d{9}")) {
            throw new ReglaNegocioException("RUC invalido: 11 digitos que empiezan con 10, 15, 16, 17 o 20");
        }
        return limpio;
    }

    private static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
