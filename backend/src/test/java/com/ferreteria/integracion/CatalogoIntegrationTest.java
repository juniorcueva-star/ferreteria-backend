package com.ferreteria.integracion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogoIntegrationTest extends IntegracionTestBase {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 0x49};

    private Map<String, Object> clavo() {
        return Map.of("codigo", "CLA-001", "nombre", "Clavo de 2 pulgadas", "marca", "Aceros",
                "categoriaId", 2, "unidadBase", "UNIDAD",
                "presentaciones", List.of(
                        Map.of("nombre", "Unidad", "factor", 1, "precioVenta", 0.10, "codigoBarras", "7750001"),
                        Map.of("nombre", "Ciento", "factor", 100, "precioVenta", 8.00),
                        Map.of("nombre", "Millar", "factor", 1000, "precioVenta", 70.00)));
    }

    @Test
    @DisplayName("ADMIN crea un producto con presentaciones; la primera queda como principal")
    void creaProductoConPresentaciones() throws Exception {
        String token = datos.token(datos.admin);
        postCon(token, "/api/productos", clavo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.presentaciones.length()").value(3))
                .andExpect(jsonPath("$.presentaciones[0].principal").value(true))
                .andExpect(jsonPath("$.presentaciones[1].principal").value(false))
                .andExpect(jsonPath("$.presentaciones[2].factor").value(1000));

        getCon(datos.token(datos.vendedor1), "/api/productos?texto=clavo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].categoriaNombre").value("Clavos y tornillos"));

        getCon(token, "/api/productos/codigo-barras/7750001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("CLA-001"));
    }

    @Test
    @DisplayName("Codigo repetido da 409 y un vendedor no puede crear productos (403)")
    void reglasDeCreacion() throws Exception {
        postCon(datos.token(datos.admin), "/api/productos", clavo()).andExpect(status().isCreated());
        postCon(datos.token(datos.admin), "/api/productos", clavo())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
        postCon(datos.token(datos.vendedor1), "/api/productos", clavo()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un producto por UNIDAD no acepta presentaciones con fraccion de unidad")
    void factorFraccionarioEnUnidad() throws Exception {
        postCon(datos.token(datos.admin), "/api/productos", Map.of("codigo", "X-1", "nombre", "Perno",
                "categoriaId", 1, "unidadBase", "UNIDAD",
                "presentaciones", List.of(Map.of("nombre", "Media", "factor", 0.5, "precioVenta", 1))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Cambiar la presentacion principal desmarca la anterior")
    void cambiarPrincipal() throws Exception {
        String token = datos.token(datos.admin);
        MvcResult creado = postCon(token, "/api/productos", clavo()).andReturn();
        Long productoId = leerId(creado, "$.id");
        Long cientoId = leerId(creado, "$.presentaciones[1].id");
        Long unidadId = leerId(creado, "$.presentaciones[0].id");

        putCon(token, "/api/productos/{id}/presentaciones/{pid}",
                Map.of("nombre", "Ciento", "factor", 100, "precioVenta", 7.50, "principal", true), productoId, cientoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentaciones[0].principal").value(false))
                .andExpect(jsonPath("$.presentaciones[1].principal").value(true))
                .andExpect(jsonPath("$.presentaciones[1].precioVenta").value(7.5));

        // No se puede dejar el producto sin principal
        putCon(token, "/api/productos/{id}/presentaciones/{pid}",
                Map.of("nombre", "Ciento", "factor", 100, "precioVenta", 7.50, "principal", false), productoId, cientoId)
                .andExpect(status().isUnprocessableContent());
        // Se puede agregar otra presentacion como principal
        postCon(token, "/api/productos/{id}/presentaciones",
                Map.of("nombre", "Docena", "factor", 12, "precioVenta", 1.10, "principal", true), productoId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.presentaciones[3].principal").value(true))
                .andExpect(jsonPath("$.presentaciones[1].principal").value(false));
        assertThat(unidadId).isNotNull();
    }

    @Test
    @DisplayName("Sube una foto PNG (guardado local) y rechaza archivos que no son imagenes")
    void subirImagen() throws Exception {
        String token = datos.token(datos.admin);
        Long productoId = leerId(postCon(token, "/api/productos", clavo()).andReturn(), "$.id");

        MvcResult resultado = mockMvc.perform(multipart("/api/productos/{id}/imagen", productoId)
                        .file(new MockMultipartFile("archivo", "foto.png", "image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String url = leer(resultado, "$.imagenUrl");
        assertThat(url).contains("/imagenes/").endsWith(".png");

        mockMvc.perform(get(url.substring(url.indexOf("/imagenes/")))).andExpect(status().isOk());

        // Al reemplazar la foto, la anterior se borra (despues de confirmar la transaccion)
        String nueva = leer(mockMvc.perform(multipart("/api/productos/{id}/imagen", productoId)
                        .file(new MockMultipartFile("archivo", "foto2.png", "image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn(), "$.imagenUrl");
        assertThat(nueva).isNotEqualTo(url);
        mockMvc.perform(get(url.substring(url.indexOf("/imagenes/")))).andExpect(status().isNotFound());
        mockMvc.perform(get(nueva.substring(nueva.indexOf("/imagenes/")))).andExpect(status().isOk());

        mockMvc.perform(multipart("/api/productos/{id}/imagen", productoId)
                        .file(new MockMultipartFile("archivo", "virus.png", "image/png", "no soy una imagen".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_ARCHIVO_NO_SOPORTADO"));
    }

    @Test
    @DisplayName("Categorias: listar para todos, crear solo ADMIN y sin duplicados")
    void categorias() throws Exception {
        getCon(datos.token(datos.vendedor1), "/api/categorias")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(9));
        postCon(datos.token(datos.admin), "/api/categorias", Map.of("nombre", "pinturas"))
                .andExpect(status().isConflict());
        postCon(datos.token(datos.vendedor1), "/api/categorias", Map.of("nombre", "Jardineria"))
                .andExpect(status().isForbidden());
    }
}
