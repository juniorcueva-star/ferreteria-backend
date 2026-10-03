package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Ubicacion;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de las pruebas de integracion: levanta la aplicacion completa contra PostgreSQL real
 * (esquema "pruebas"), limpia las tablas y crea los datos basicos antes de cada prueba.
 * Las peticiones pasan por todo el stack: filtro JWT, controller, service, repository y BD.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegracionTestBase {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected DatosPrueba datos;

    @BeforeEach
    void prepararDatos() {
        datos.reiniciar();
    }

    protected ResultActions getCon(String token, String url, Object... vars) throws Exception {
        return mockMvc.perform(get(url, vars).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    protected ResultActions postCon(String token, String url, Object cuerpo, Object... vars) throws Exception {
        return mockMvc.perform(post(url, vars).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)));
    }

    protected ResultActions putCon(String token, String url, Object cuerpo, Object... vars) throws Exception {
        return mockMvc.perform(put(url, vars).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)));
    }

    protected ResultActions deleteCon(String token, String url, Object... vars) throws Exception {
        return mockMvc.perform(delete(url, vars).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    /** Fecha de hoy en Lima: el sistema cuenta los dias en la zona del negocio, no en la del servidor. */
    protected static LocalDate hoyLima() {
        return LocalDate.now(ZoneId.of("America/Lima"));
    }

    /** Carga stock inicial (en unidad base) con el ADMIN, por la API. */
    protected void cargarStock(Producto producto, Ubicacion ubicacion, Number cantidad) throws Exception {
        postCon(datos.token(datos.admin), "/api/inventario/inicial", Map.of("ubicacionId", ubicacion.getId(),
                "detalles", List.of(Map.of("productoId", producto.getId(), "cantidad", cantidad))))
                .andExpect(status().isCreated());
    }

    /** Lee un valor de la respuesta JSON. Ej: leer(resultado, "$.id"). */
    protected static <T> T leer(MvcResult resultado, String ruta) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(), ruta);
    }

    protected static Long leerId(MvcResult resultado, String ruta) throws Exception {
        Number numero = leer(resultado, ruta);
        return numero.longValue();
    }
}
