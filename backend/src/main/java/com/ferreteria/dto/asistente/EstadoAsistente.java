package com.ferreteria.dto.asistente;

import java.util.List;

/**
 * Para abrir el chat.
 *
 * @param configurado false si falta la clave de la IA en el servidor
 * @param nombre      nombre del asistente segun el rol (Ej: "Asistente del almacén")
 * @param sugerencias preguntas frecuentes del rol, para tocarlas sin escribir
 */
public record EstadoAsistente(boolean configurado, String nombre, String saludo, List<String> sugerencias,
                              int preguntasRestantes, int preguntasPorDia) {
}
