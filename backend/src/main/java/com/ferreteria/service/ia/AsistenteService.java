package com.ferreteria.service.ia;

import com.ferreteria.dto.asistente.EstadoAsistente;
import com.ferreteria.dto.asistente.PreguntaRequest;
import com.ferreteria.dto.asistente.RespuestaAsistente;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.UbicacionRepository;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.security.UsuarioActual;
import com.ferreteria.service.Calendario;
import com.ferreteria.service.ia.ModeloIa.Mensaje;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asistente de IA de apoyo para cada rol (pedido del dueno, version basica): SOLO consulta y responde con los datos
 * del sistema; no registra ni cambia nada. Cada pregunta puede usar hasta {@value #MAXIMO_PASOS} consultas. Hay un
 * tope de preguntas por usuario al dia para controlar el gasto (el contador esta en memoria: se reinicia al cambiar
 * el dia o al reiniciar el servidor).
 */
@Slf4j
@Service
public class AsistenteService {

    static final int MAXIMO_PASOS = 5;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy",
            Locale.forLanguageTag("es-PE"));

    private final ModeloIa modelo;
    private final HerramientasAsistente herramientas;
    private final AccesoUbicacionService accesoUbicacion;
    private final UbicacionRepository ubicacionRepository;
    private final Calendario calendario;
    private final int preguntasPorDia;
    private final Map<Long, Uso> uso = new ConcurrentHashMap<>();

    private record Uso(LocalDate dia, int preguntas) {
    }

    private record Perfil(String nombre, String saludo, String puede, List<String> sugerencias) {
    }

    private static final Map<Rol, Perfil> PERFILES = Map.of(
            Rol.ALMACENERO, new Perfil("Asistente del almacén",
                    "Hola, te ayudo a ubicar productos y a ver qué falta reponer.",
                    "ubicar un producto (dónde está y cuánto hay), ver qué productos están bajo el stock mínimo y "
                            + "revisar los pedidos de reposición pendientes de las tiendas",
                    List.of("¿Dónde está el perno 3/8?", "¿Qué productos me faltan?", "¿Qué pedidos tengo pendientes?")),
            Rol.VENDEDOR, new Perfil("Asistente de ventas",
                    "Hola, te ayudo a encontrar productos, ver precios y si hay en otra tienda.",
                    "buscar productos aunque el cliente los nombre a su manera, ver si hay stock en otras tiendas o "
                            + "en el almacén y calcular cuánto cuesta una cantidad (con oferta o precio por mayor)",
                    List.of("Tarugo para pared con su tornillo", "¿Hay clavo de 3 pulgadas en otra tienda?",
                            "¿Cuánto cuestan 100 pernos 3/8?")),
            Rol.ADMIN, new Perfil("Asistente del negocio",
                    "Hola, te cuento cómo va el negocio: ventas, lo más vendido y quién debe.",
                    "ver las ventas de cada tienda (hoy, ayer o entre fechas), los productos más vendidos y los "
                            + "clientes que deben; también buscar productos, precios y stock, y lo que falta reponer",
                    List.of("¿Cómo me fue hoy?", "¿Qué es lo que más se vendió esta semana?", "¿Quién me debe?")));

    public AsistenteService(ModeloIa modelo, HerramientasAsistente herramientas, AccesoUbicacionService accesoUbicacion,
                            UbicacionRepository ubicacionRepository, Calendario calendario,
                            @Value("${app.ia.preguntas-por-dia:50}") int preguntasPorDia) {
        this.modelo = modelo;
        this.herramientas = herramientas;
        this.accesoUbicacion = accesoUbicacion;
        this.ubicacionRepository = ubicacionRepository;
        this.calendario = calendario;
        this.preguntasPorDia = preguntasPorDia;
    }

    public EstadoAsistente estado() {
        UsuarioActual usuario = accesoUbicacion.usuarioActual();
        Perfil perfil = perfil(usuario.rol());
        return new EstadoAsistente(modelo.configurado(), perfil.nombre(), perfil.saludo(), perfil.sugerencias(),
                restantes(usuario.id()), preguntasPorDia);
    }

    public RespuestaAsistente preguntar(PreguntaRequest request) {
        if (!modelo.configurado()) {
            throw new ReglaNegocioException("El asistente no está configurado: falta la clave de la IA en el "
                    + "servidor (ver docs/GUIA_CLAVE_IA.md)");
        }
        UsuarioActual usuario = accesoUbicacion.usuarioActual();
        contar(usuario.id());

        List<Mensaje> conversacion = new ArrayList<>();
        for (PreguntaRequest.MensajeChat m : request.historial() == null ? List.<PreguntaRequest.MensajeChat>of()
                : request.historial()) {
            conversacion.add("USUARIO".equals(m.rol()) ? new ModeloIa.DelUsuario(m.texto())
                    : new ModeloIa.DelAsistente(m.texto()));
        }
        conversacion.add(new ModeloIa.DelUsuario(request.pregunta().strip()));

        String sistema = sistema(usuario);
        List<ModeloIa.Herramienta> disponibles = herramientas.para(usuario.rol());
        Set<String> consultas = new LinkedHashSet<>();
        long entrada = 0;
        long salida = 0;
        ModeloIa.Turno turno = null;
        for (int paso = 0; paso < MAXIMO_PASOS; paso++) {
            turno = modelo.responder(sistema, disponibles, conversacion);
            entrada += turno.tokensEntrada();
            salida += turno.tokensSalida();
            if (!"TOOL_USE".equals(turno.fin()) || turno.llamadas().isEmpty()) {
                break;
            }
            conversacion.add(new ModeloIa.DelModelo(turno));
            List<ModeloIa.Resultado> resultados = new ArrayList<>();
            for (ModeloIa.Llamada llamada : turno.llamadas()) {
                consultas.add(llamada.nombre());
                resultados.add(herramientas.ejecutar(usuario.rol(), llamada));
            }
            conversacion.add(new ModeloIa.Resultados(resultados));
        }
        log.info("Asistente: usuario {} ({}) consultas={} tokens entrada={} salida={}", usuario.username(),
                usuario.rol(), consultas, entrada, salida);
        return new RespuestaAsistente(texto(turno), List.copyOf(consultas), restantes(usuario.id()));
    }

    private static String texto(ModeloIa.Turno turno) {
        if (turno == null) {
            return "No encontré una respuesta. Intente preguntar de otra forma.";
        }
        if ("REFUSAL".equals(turno.fin())) {
            return "No puedo ayudar con eso. Pregúnteme por productos, precios, stock o ventas.";
        }
        if ("TOOL_USE".equals(turno.fin())) {
            return "La pregunta necesitaba demasiadas consultas. Intente con una pregunta más corta o concreta.";
        }
        String texto = turno.texto();
        return texto.isBlank() ? "No encontré una respuesta. Intente preguntar de otra forma." : texto;
    }

    /** Suma una pregunta del dia; si ya llego al tope, avisa sin llamar a la IA. */
    private void contar(Long usuarioId) {
        LocalDate hoy = calendario.hoy();
        boolean[] lleno = {false};
        uso.compute(usuarioId, (id, previo) -> {
            int hechas = previo == null || !previo.dia().equals(hoy) ? 0 : previo.preguntas();
            if (hechas >= preguntasPorDia) {
                lleno[0] = true;
                return new Uso(hoy, hechas);
            }
            return new Uso(hoy, hechas + 1);
        });
        if (lleno[0]) {
            throw new ReglaNegocioException("Se llegó al límite de " + preguntasPorDia + " preguntas de hoy. "
                    + "Mañana se reinicia");
        }
    }

    private int restantes(Long usuarioId) {
        Uso actual = uso.get(usuarioId);
        if (actual == null || !actual.dia().equals(calendario.hoy())) {
            return preguntasPorDia;
        }
        return Math.max(0, preguntasPorDia - actual.preguntas());
    }

    private String sistema(UsuarioActual usuario) {
        Perfil perfil = perfil(usuario.rol());
        String lugar = usuario.ubicacionId() == null ? "" : ubicacionRepository.findById(usuario.ubicacionId())
                .map(Ubicacion::getNombre).map(n -> " de " + n).orElse("");
        LocalDate hoy = calendario.hoy();
        return """
                Eres el %s de una ferretería en Perú. Hablas con %s (%s%s).
                Hoy es %s (fecha %s, hora de Lima).

                Puedes ayudar a: %s.

                Reglas:
                - Responde en español sencillo y corto: como máximo 6 líneas o una lista corta. Sin tecnicismos.
                - Para precios, stock, ventas o deudas usa SIEMPRE las herramientas; nunca inventes datos. Si una
                  herramienta no trae el dato, dilo.
                - Precios en soles con el formato S/ 0.00 (incluyen IGV). Para el total de una cantidad usa
                  calcular_precio en vez de hacer la cuenta tú.
                - Si no encuentras un producto, busca otra vez con un sinónimo o con menos palabras (Ej: tarugo ->
                  taco; "3 pulgadas" -> 3"). Si aun así no está, dilo.
                - Solo consultas: si te piden vender, registrar, cambiar precios o stock, explica que eso se hace en
                  la pantalla del sistema.
                - Para "hoy" usa desde = hasta = %s; para "esta semana", desde el lunes hasta hoy.
                - Si preguntan algo que no es de la ferretería, responde en una línea que solo ayudas con el negocio.
                """.formatted(perfil.nombre().toLowerCase(Locale.ROOT), usuario.nombres(), rol(usuario.rol()), lugar,
                FECHA.format(hoy), hoy, perfil.puede(), hoy);
    }

    private static String rol(Rol rol) {
        return switch (rol) {
            case ADMIN -> "dueño / administrador";
            case VENDEDOR -> "vendedor";
            case ALMACENERO -> "almacenero";
        };
    }

    private static Perfil perfil(Rol rol) {
        return PERFILES.get(rol);
    }
}
