package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.service.ia.ModeloIa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asistente de IA basico: el modelo es simulado (no se llama a la API real). Se prueba que cada rol recibe solo sus
 * herramientas, que las consultas usan los datos y permisos reales, y el tope diario.
 */
@TestPropertySource(properties = "app.ia.preguntas-por-dia=3")
class AsistenteIntegrationTest extends IntegracionTestBase {

    @MockitoBean private ModeloIa modelo;

    private Producto perno;

    @BeforeEach
    void preparar() throws Exception {
        perno = datos.producto("PER-IA", UnidadBase.UNIDAD, "0.50");
        cargarStock(perno, datos.tienda1, 40);
        cargarStock(perno, datos.almacen, 850);
        when(modelo.configurado()).thenReturn(true);
    }

    private static ModeloIa.Turno pide(String herramienta, Map<String, Object> entrada) {
        return new ModeloIa.Turno(List.of(new ModeloIa.Llamada("t1", herramienta, entrada)), "TOOL_USE", null, 100, 20);
    }

    private static ModeloIa.Turno responde(String texto) {
        return new ModeloIa.Turno(List.of(new ModeloIa.Texto(texto)), "END_TURN", null, 150, 40);
    }

    @SuppressWarnings("unchecked")
    private List<ModeloIa.Mensaje> conversacionFinal() {
        ArgumentCaptor<List<ModeloIa.Mensaje>> conversacion = ArgumentCaptor.forClass(List.class);
        verify(modelo, times(2)).responder(anyString(), anyList(), conversacion.capture());
        return conversacion.getValue();
    }

    private static String resultado(List<ModeloIa.Mensaje> conversacion) {
        return conversacion.stream().filter(ModeloIa.Resultados.class::isInstance)
                .map(m -> ((ModeloIa.Resultados) m).resultados().getFirst().contenido())
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("El vendedor busca un producto: recibe solo sus herramientas y ve el stock de todas las ubicaciones")
    void vendedorBuscaProducto() throws Exception {
        when(modelo.responder(anyString(), anyList(), anyList()))
                .thenReturn(pide("buscar_productos", Map.of("texto", "PER-IA")), responde("Hay 40 en tu tienda."));
        String vendedor = datos.token(datos.vendedor1);
        // El contador del dia vive en memoria: se compara con lo que quedaba antes de preguntar
        int antes = leer(getCon(vendedor, "/api/asistente/estado").andReturn(), "$.preguntasRestantes");
        postCon(vendedor, "/api/asistente/preguntar", Map.of("pregunta", "¿Hay perno?",
                        "historial", List.of(Map.of("rol", "USUARIO", "texto", "Hola"),
                                Map.of("rol", "ASISTENTE", "texto", "Hola, ¿en qué te ayudo?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respuesta").value("Hay 40 en tu tienda."))
                .andExpect(jsonPath("$.consultas[0]").value("buscar_productos"))
                .andExpect(jsonPath("$.preguntasRestantes").value(antes - 1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModeloIa.Herramienta>> ofrecidas = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> sistema = ArgumentCaptor.forClass(String.class);
        verify(modelo, times(2)).responder(sistema.capture(), ofrecidas.capture(), anyList());
        assertThat(ofrecidas.getValue()).extracting(ModeloIa.Herramienta::nombre)
                .containsExactly("buscar_productos", "calcular_precio");
        assertThat(sistema.getValue()).contains("asistente de ventas", "vendedor");
        String json = resultado(conversacionFinal());
        assertThat(json).contains("PER-IA", "precioLista", "850", "40");
        // El historial del chat llega al modelo antes de la pregunta
        assertThat(conversacionFinal().getFirst()).isEqualTo(new ModeloIa.DelUsuario("Hola"));
    }

    @Test
    @DisplayName("Una consulta que no es del rol no se ejecuta aunque el modelo la pida")
    void herramientaDeOtroRol() throws Exception {
        when(modelo.responder(anyString(), anyList(), anyList()))
                .thenReturn(pide("deudores", Map.of()), responde("No puedo ver deudas."));
        postCon(datos.token(datos.vendedor1), "/api/asistente/preguntar", Map.of("pregunta", "¿Quién me debe?"))
                .andExpect(status().isOk());
        List<ModeloIa.Mensaje> conversacion = conversacionFinal();
        ModeloIa.Resultado res = conversacion.stream().filter(ModeloIa.Resultados.class::isInstance)
                .map(m -> ((ModeloIa.Resultados) m).resultados().getFirst()).findFirst().orElseThrow();
        assertThat(res.error()).isTrue();
        assertThat(res.contenido()).contains("no esta disponible para su rol");
    }

    @Test
    @DisplayName("Calcular precio aplica el precio por mayor y da el total")
    void calcularPrecio() throws Exception {
        String admin = datos.token(datos.admin);
        Long unidad = datos.presentacionId(perno, 0);
        putCon(admin, "/api/productos/" + perno.getId() + "/presentaciones/" + unidad + "/escalas",
                Map.of("escalas", List.of(Map.of("cantidadMinima", 100, "precio", 0.40)))).andExpect(status().isOk());
        when(modelo.responder(anyString(), anyList(), anyList()))
                .thenReturn(pide("calcular_precio", Map.of("presentacionId", unidad, "cantidad", 100)),
                        responde("100 pernos: S/ 40.00"));
        postCon(datos.token(datos.vendedor1), "/api/asistente/preguntar", Map.of("pregunta", "¿Cuánto cuestan 100?"))
                .andExpect(status().isOk());
        assertThat(resultado(conversacionFinal())).contains("\"tipoPrecio\":\"MAYOR\"", "\"total\":40.00");
    }

    @Test
    @DisplayName("El dueño ve las ventas por tienda; el almacenero recibe sus herramientas de reposicion")
    void herramientasDelDuenoYAlmacenero() throws Exception {
        String hoy = hoyLima().toString();
        when(modelo.responder(anyString(), anyList(), anyList()))
                .thenReturn(pide("ventas_por_tienda", Map.of("desde", hoy, "hasta", hoy)), responde("Hoy no hay ventas."));
        postCon(datos.token(datos.admin), "/api/asistente/preguntar", Map.of("pregunta", "¿Cómo me fue hoy?"))
                .andExpect(status().isOk());
        assertThat(resultado(conversacionFinal())).startsWith("[");

        getCon(datos.token(datos.almacenero), "/api/asistente/estado")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurado").value(true))
                .andExpect(jsonPath("$.nombre").value("Asistente del almacén"))
                .andExpect(jsonPath("$.sugerencias.length()").value(3))
                .andExpect(jsonPath("$.preguntasPorDia").value(3));
    }

    @Test
    @DisplayName("Sin clave avisa que no esta configurado; con el tope diario lleno no llama a la IA")
    void sinClaveYTopeDiario() throws Exception {
        String vendedor = datos.token(datos.vendedor1);
        when(modelo.configurado()).thenReturn(false);
        postCon(vendedor, "/api/asistente/preguntar", Map.of("pregunta", "Hola"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value(containsString("no está configurado")));
        verify(modelo, never()).responder(anyString(), anyList(), anyList());

        when(modelo.configurado()).thenReturn(true);
        when(modelo.responder(anyString(), anyList(), anyList())).thenReturn(responde("Hola"));
        String almacenero = datos.token(datos.almacenero);
        int restantes = leer(getCon(almacenero, "/api/asistente/estado").andReturn(), "$.preguntasRestantes");
        for (int i = 0; i < restantes; i++) {
            postCon(almacenero, "/api/asistente/preguntar", Map.of("pregunta", "Hola")).andExpect(status().isOk());
        }
        postCon(almacenero, "/api/asistente/preguntar", Map.of("pregunta", "Hola"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value(containsString("límite de 3 preguntas")));
        postCon(almacenero, "/api/asistente/preguntar", Map.of("pregunta", ""))
                .andExpect(status().isBadRequest());
    }
}
