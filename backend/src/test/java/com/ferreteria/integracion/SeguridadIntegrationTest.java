package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de seguridad de toda la API: sin token (401), rol incorrecto (403) y aislamiento por tienda (403).
 */
class SeguridadIntegrationTest extends IntegracionTestBase {

    @ParameterizedTest(name = "GET {0} sin token responde 401")
    @ValueSource(strings = {"/api/auth/me", "/api/empresas", "/api/ubicaciones", "/api/usuarios", "/api/categorias",
            "/api/productos", "/api/proveedores", "/api/compras", "/api/traslados", "/api/inventario/stock",
            "/api/inventario/kardex", "/api/clientes", "/api/cajas", "/api/cajas/actual", "/api/ventas",
            "/api/metodos-pago", "/api/fiado/deudas", "/api/fiado/deudores", "/api/reportes/stock-bajo"})
    void sinTokenDa401(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @ParameterizedTest(name = "POST {0} sin token responde 401")
    @ValueSource(strings = {"/api/productos", "/api/compras", "/api/traslados", "/api/ventas", "/api/cajas/abrir",
            "/api/inventario/ajustes", "/api/fiado/ventas/1/abonos"})
    void escrituraSinTokenDa401(String url) throws Exception {
        mockMvc.perform(post(url).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Rol incorrecto responde 403 ACCESO_DENEGADO")
    void rolIncorrectoDa403() throws Exception {
        String vendedor = datos.token(datos.vendedor1);
        String almacenero = datos.token(datos.almacenero);
        getCon(vendedor, "/api/usuarios").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        getCon(vendedor, "/api/proveedores").andExpect(status().isForbidden());
        getCon(vendedor, "/api/compras").andExpect(status().isForbidden());
        postCon(vendedor, "/api/inventario/ajustes", Map.of()).andExpect(status().isForbidden());
        postCon(vendedor, "/api/inventario/inicial", Map.of()).andExpect(status().isForbidden());
        postCon(vendedor, "/api/productos", Map.of()).andExpect(status().isForbidden());
        getCon(almacenero, "/api/ventas").andExpect(status().isForbidden());
        getCon(almacenero, "/api/cajas").andExpect(status().isForbidden());
        getCon(almacenero, "/api/clientes").andExpect(status().isForbidden());
        getCon(almacenero, "/api/fiado/deudores").andExpect(status().isForbidden());
        postCon(almacenero, "/api/compras/1/anular", Map.of("motivo", "No puedo anular")).andExpect(status().isForbidden());
        postCon(almacenero, "/api/categorias", Map.of("nombre", "X")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El vendedor de la tienda 1 no ve ni opera datos de la tienda 2")
    void aislamientoPorTienda() throws Exception {
        Producto foco = datos.producto("FOC-1", UnidadBase.UNIDAD, "5.00");
        cargarStock(foco, datos.tienda2, 10);
        String v2 = datos.token(datos.vendedor2);
        Long cajaT2 = leerId(postCon(v2, "/api/cajas/abrir", Map.of("montoApertura", 0)).andReturn(), "$.id");
        Long ventaT2 = leerId(postCon(v2, "/api/ventas", Map.of("condicion", "CONTADO",
                "detalles", List.of(Map.of("presentacionId", datos.presentacionId(foco, 0), "cantidad", 1)),
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 5)))).andReturn(), "$.id");
        Long t2 = datos.tienda2.getId();

        String v1 = datos.token(datos.vendedor1);
        // Lecturas directas por id
        getCon(v1, "/api/ventas/{id}", ventaT2).andExpect(status().isForbidden());
        getCon(v1, "/api/cajas/{id}", cajaT2).andExpect(status().isForbidden());
        // Filtros explicitos hacia la otra tienda
        for (String url : List.of("/api/ventas", "/api/cajas", "/api/inventario/stock", "/api/inventario/kardex",
                "/api/fiado/deudas", "/api/fiado/deudores", "/api/reportes/stock-bajo", "/api/traslados")) {
            getCon(v1, url + "?ubicacionId={id}", t2).andExpect(status().isForbidden());
        }
        // Listados sin filtro: solo lo propio
        getCon(v1, "/api/ventas").andExpect(jsonPath("$.totalElementos").value(0));
        getCon(v1, "/api/inventario/stock").andExpect(jsonPath("$.totalElementos").value(0));
        // Operaciones sobre datos de la otra tienda
        postCon(v1, "/api/ventas/{id}/anular", Map.of("motivo", "Intento indebido"), ventaT2)
                .andExpect(status().isForbidden());
        postCon(v1, "/api/cajas/{id}/cerrar", Map.of("efectivoContado", 0), cajaT2).andExpect(status().isForbidden());
        postCon(v1, "/api/cajas/abrir", Map.of("ubicacionId", t2, "montoApertura", 0)).andExpect(status().isForbidden());
        // El ADMIN si puede ver la venta
        getCon(datos.token(datos.admin), "/api/ventas/{id}", ventaT2).andExpect(status().isOk());
    }

    @Test
    @DisplayName("La documentacion OpenAPI y Swagger UI son publicas y documentan el formato de error")
    void documentacionPublica() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
                .andExpect(jsonPath("$.paths['/api/ventas'].post.responses['422']").exists());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
