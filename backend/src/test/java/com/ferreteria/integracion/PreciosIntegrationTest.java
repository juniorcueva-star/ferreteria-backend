package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Precio sugerido (D9 y D10) e historial de precios (D11).
 */
class PreciosIntegrationTest extends IntegracionTestBase {

    private Producto perno;
    private Long proveedorId;
    private String admin;

    @BeforeEach
    void preparar() {
        perno = datos.producto("PER-1", UnidadBase.UNIDAD, "2.50", "Caja x50", "50", "110.00");
        proveedorId = datos.proveedor("20888888881").getId();
        admin = datos.token(datos.admin);
    }

    /** Compra de cajas x50 al precio indicado por caja. */
    private void comprarCajas(int cajas, Number precioCaja) throws Exception {
        postCon(datos.token(datos.almacenero), "/api/compras", Map.of("empresaId", datos.empresa1.getId(),
                "proveedorId", proveedorId, "tipoComprobante", "BOLETA", "fechaEmision", hoyLima().toString(),
                "detalles", List.of(Map.of("productoId", perno.getId(), "presentacionId",
                        datos.presentacionId(perno, 1), "cantidad", cajas, "precioUnitario", precioCaja))))
                .andExpect(status().isCreated());
    }

    private Map<String, Object> caja(Number precio, Number ganancia, Number adicional) {
        return Map.of("nombre", "Caja x50", "factor", 50, "precioVenta", precio, "porcentajeGanancia", ganancia,
                "montoAdicional", adicional);
    }

    @Test
    @DisplayName("Una compra fija el costo de referencia de todas las presentaciones sin tocar el precio final")
    void compraActualizaCosto() throws Exception {
        comprarCajas(2, 100); // 2.0000 por unidad base

        getCon(admin, "/api/productos/{id}/presentaciones/{p}/precio-sugerido", perno.getId(),
                datos.presentacionId(perno, 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costoReferencia").value(2.0))
                .andExpect(jsonPath("$.precioVenta").value(2.5));
        getCon(admin, "/api/productos/{id}/presentaciones/{p}/precio-sugerido", perno.getId(),
                datos.presentacionId(perno, 1))
                .andExpect(jsonPath("$.costoReferencia").value(100.0))
                .andExpect(jsonPath("$.precioSugerido").value(100.0));
    }

    @Test
    @DisplayName("Precio sugerido con ganancia y adicional; vista previa sin guardar; el precio final no cambia solo")
    void precioSugerido() throws Exception {
        comprarCajas(1, 100);
        putCon(admin, "/api/productos/{id}/presentaciones/{p}", caja(110, 30, 0.5), perno.getId(),
                datos.presentacionId(perno, 1)).andExpect(status().isOk());

        String url = "/api/productos/{id}/presentaciones/{p}/precio-sugerido";
        getCon(admin, url, perno.getId(), datos.presentacionId(perno, 1))
                .andExpect(jsonPath("$.precioSugerido").value(130.5))
                .andExpect(jsonPath("$.precioVenta").value(110.0));
        getCon(admin, url + "?porcentajeGanancia=10", perno.getId(), datos.presentacionId(perno, 1))
                .andExpect(jsonPath("$.precioSugerido").value(110.5));

        comprarCajas(1, 120); // el costo sube: cambia el sugerido, no el precio final
        getCon(admin, url, perno.getId(), datos.presentacionId(perno, 1))
                .andExpect(jsonPath("$.costoReferencia").value(120.0))
                .andExpect(jsonPath("$.precioSugerido").value(156.5))
                .andExpect(jsonPath("$.precioVenta").value(110.0));
    }

    @Test
    @DisplayName("Cada cambio del precio final queda en el historial; guardar el mismo precio no agrega fila")
    void historial() throws Exception {
        String url = "/api/productos/{id}/presentaciones/{p}";
        Long caja = datos.presentacionId(perno, 1);
        putCon(admin, url, caja(115, 30, 0), perno.getId(), caja).andExpect(status().isOk());
        putCon(admin, url, caja(115, 25, 0), perno.getId(), caja).andExpect(status().isOk());
        putCon(admin, url, caja(118.9, 25, 0), perno.getId(), caja).andExpect(status().isOk());

        getCon(admin, url + "/historial-precios", perno.getId(), caja)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].precioAnterior").value(115.0))
                .andExpect(jsonPath("$.contenido[0].precioNuevo").value(118.9))
                .andExpect(jsonPath("$.contenido[0].usuario").value("admin"))
                .andExpect(jsonPath("$.contenido[1].precioAnterior").value(110.0));
    }

    @Test
    @DisplayName("El costo y el historial solo los ve el ADMIN")
    void soloAdmin() throws Exception {
        String vendedor = datos.token(datos.vendedor1);
        getCon(vendedor, "/api/productos/{id}/presentaciones/{p}/precio-sugerido", perno.getId(),
                datos.presentacionId(perno, 1)).andExpect(status().isForbidden());
        getCon(vendedor, "/api/productos/{id}/presentaciones/{p}/historial-precios", perno.getId(),
                datos.presentacionId(perno, 1)).andExpect(status().isForbidden());
        getCon(vendedor, "/api/productos/{id}", perno.getId()).andExpect(status().isOk());
    }
}
