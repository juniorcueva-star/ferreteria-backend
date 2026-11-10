package com.ferreteria.dto.asistente;

import java.util.List;

/**
 * @param consultas          lo que el asistente reviso para responder (Ej: buscar_productos)
 * @param preguntasRestantes preguntas que le quedan hoy al usuario
 */
public record RespuestaAsistente(String respuesta, List<String> consultas, int preguntasRestantes) {
}
