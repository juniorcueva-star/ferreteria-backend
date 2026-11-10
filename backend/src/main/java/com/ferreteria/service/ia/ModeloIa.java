package com.ferreteria.service.ia;

import java.util.List;
import java.util.Map;

/**
 * El modelo de IA que responde al asistente. Se separa del SDK para probar el asistente sin llamar a la API real
 * (las pruebas usan un modelo de mentira con respuestas preparadas).
 */
public interface ModeloIa {

    /** true si hay clave de API configurada. */
    boolean configurado();

    /** Un turno del modelo: texto para el usuario y/o pedidos de herramientas. */
    Turno responder(String sistema, List<Herramienta> herramientas, List<Mensaje> conversacion);

    /** Herramienta que el modelo puede pedir. esquema = JSON Schema de su entrada (propiedades). */
    record Herramienta(String nombre, String descripcion, Map<String, Object> propiedades, List<String> requeridas) {
    }

    /** Parte de una respuesta del modelo. */
    sealed interface Bloque permits Texto, Llamada {
    }

    record Texto(String texto) implements Bloque {
    }

    /** El modelo pide ejecutar una herramienta con esta entrada. */
    record Llamada(String id, String nombre, Map<String, Object> entrada) implements Bloque {
    }

    /**
     * @param fin      END_TURN, TOOL_USE, MAX_TOKENS, REFUSAL u otro motivo de fin
     * @param original la respuesta del SDK tal cual (se devuelve igual al modelo en el siguiente paso, con sus
     *                 bloques de razonamiento); null en las pruebas
     */
    record Turno(List<Bloque> bloques, String fin, Object original, long tokensEntrada, long tokensSalida) {

        public List<Llamada> llamadas() {
            return bloques.stream().filter(Llamada.class::isInstance).map(Llamada.class::cast).toList();
        }

        public String texto() {
            return String.join("\n", bloques.stream().filter(Texto.class::isInstance)
                    .map(b -> ((Texto) b).texto()).toList()).strip();
        }
    }

    /** Mensaje de la conversacion que se envia al modelo. */
    sealed interface Mensaje permits DelUsuario, DelAsistente, DelModelo, Resultados {
    }

    /** Lo que escribio el usuario. */
    record DelUsuario(String texto) implements Mensaje {
    }

    /** Respuesta anterior del asistente (solo texto, de preguntas anteriores del chat). */
    record DelAsistente(String texto) implements Mensaje {
    }

    /** Turno del modelo dentro de esta pregunta (con sus pedidos de herramientas). */
    record DelModelo(Turno turno) implements Mensaje {
    }

    /** Resultados de las herramientas pedidas en el turno anterior. */
    record Resultados(List<Resultado> resultados) implements Mensaje {
    }

    record Resultado(String llamadaId, String contenido, boolean error) {
    }
}
