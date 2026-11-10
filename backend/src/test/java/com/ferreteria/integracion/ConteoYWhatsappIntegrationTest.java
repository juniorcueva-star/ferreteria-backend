package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Inventario fisico, nota de venta por WhatsApp (con enlace publico del PDF) y recordatorio de deudas. */
class ConteoYWhatsappIntegrationTest extends IntegracionTestBase {

    private Producto perno;
    private Producto martillo;
    private Producto clavo;
    private String vendedor;

    @BeforeEach
    void preparar() throws Exception {
        perno = datos.producto("PER-1", UnidadBase.UNIDAD, "0.50");
        martillo = datos.producto("MAR-1", UnidadBase.UNIDAD, "30.00");
        clavo = datos.producto("CLA-1", UnidadBase.UNIDAD, "0.10");
        cargarStock(perno, datos.almacen, 100);
        cargarStock(martillo, datos.almacen, 10);
        cargarStock(perno, datos.tienda1, 50);
        vendedor = datos.token(datos.vendedor1);
    }

    @Test
    @DisplayName("Inventario fisico: ajusta faltantes y sobrantes, no toca lo que coincide y queda en la auditoria")
    void conteo() throws Exception {
        String almacenero = datos.token(datos.almacenero);
        postCon(almacenero, "/api/inventario/conteo", Map.of("observacion", "Fin de mes", "detalles", List.of(
                        Map.of("productoId", perno.getId(), "cantidadContada", 97),
                        Map.of("productoId", martillo.getId(), "cantidadContada", 12),
                        Map.of("productoId", clavo.getId(), "cantidadContada", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productosContados").value(3))
                .andExpect(jsonPath("$.conDiferencia").value(2))
                .andExpect(jsonPath("$.lineas[?(@.productoId == " + perno.getId() + ")].diferencia").value(-3.0))
                .andExpect(jsonPath("$.lineas[?(@.productoId == " + martillo.getId() + ")].diferencia").value(2.0));
        assertThat(datos.stock(perno, datos.almacen)).isEqualByComparingTo("97");
        assertThat(datos.stock(martillo, datos.almacen)).isEqualByComparingTo("12");
        getCon(almacenero, "/api/inventario/kardex?productoId={id}", perno.getId())
                .andExpect(jsonPath("$.contenido[0].tipo").value("AJUSTE_SALIDA"))
                .andExpect(jsonPath("$.contenido[0].motivo").value(containsString("Inventario fisico")));
        getCon(datos.token(datos.admin), "/api/auditoria?entidad=STOCK&texto=Inventario fisico")
                .andExpect(jsonPath("$.contenido.length()").value(2));

        postCon(almacenero, "/api/inventario/conteo", Map.of("detalles",
                        List.of(Map.of("productoId", perno.getId(), "cantidadContada", 1.5))))
                .andExpect(status().isUnprocessableContent());
        postCon(almacenero, "/api/inventario/conteo", Map.of("detalles",
                        List.of(Map.of("productoId", perno.getId(), "cantidadContada", -1))))
                .andExpect(status().isBadRequest());
        postCon(vendedor, "/api/inventario/conteo", Map.of("detalles",
                List.of(Map.of("productoId", perno.getId(), "cantidadContada", 1)))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Nota de venta por WhatsApp: usa el celular del cliente y el PDF se descarga sin iniciar sesion")
    void notaPorWhatsapp() throws Exception {
        Long cliente = leerId(postCon(vendedor, "/api/clientes", Map.of("nombre", "Juan Perez", "telefono", "987654321"))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        Long venta = leerId(postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO", "clienteId", cliente,
                        "detalles", List.of(Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 4)),
                        "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 2))))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        String json = getCon(vendedor, "/api/ventas/{id}/enlace-whatsapp", venta)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith("https://wa.me/51987654321?text=")))
                .andExpect(jsonPath("$.mensaje").value(containsString("Juan Perez")))
                .andExpect(jsonPath("$.mensaje").value(containsString("S/ 2.00")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String mensaje = JsonPath.read(json, "$.mensaje");
        String token = mensaje.substring(mensaje.indexOf("/api/publico/ventas/") + 20, mensaje.indexOf("/pdf"));
        byte[] pdf = mockMvc.perform(get("/api/publico/ventas/{t}/pdf", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        mockMvc.perform(get("/api/publico/ventas/{t}/pdf", "token-falso")).andExpect(status().isNotFound());
        // El enlace se puede pedir de nuevo con otro celular y el token no cambia
        getCon(vendedor, "/api/ventas/{id}/enlace-whatsapp?telefono=912345678", venta)
                .andExpect(jsonPath("$.url").value(startsWith("https://wa.me/51912345678")))
                .andExpect(jsonPath("$.mensaje").value(containsString(token)));
        getCon(vendedor, "/api/ventas/{id}/enlace-whatsapp?telefono=12345", venta).andExpect(status().isUnprocessableContent());
        getCon(datos.token(datos.vendedor2), "/api/ventas/{id}/enlace-whatsapp", venta).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Recordatorio de deuda por WhatsApp con las ventas pendientes y el total")
    void recordatorioDeDeuda() throws Exception {
        Long cliente = leerId(postCon(vendedor, "/api/clientes", Map.of("nombre", "Maria Lopez", "telefono", "912345678"))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        getCon(vendedor, "/api/fiado/clientes/{id}/recordatorio-whatsapp", cliente)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value(containsString("no tiene deudas")));
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CREDITO", "clienteId", cliente,
                "fechaVencimiento", hoyLima().plusDays(10).toString(),
                "detalles", List.of(Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 20)),
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 4)))).andExpect(status().isCreated());
        getCon(vendedor, "/api/fiado/clientes/{id}/recordatorio-whatsapp", cliente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith("https://wa.me/51912345678?text=")))
                .andExpect(jsonPath("$.mensaje").value(containsString("saldo pendiente")))
                .andExpect(jsonPath("$.mensaje").value(containsString("vence el")))
                .andExpect(jsonPath("$.mensaje").value(containsString("Total: S/ 6.00")));
        // El vendedor de otra tienda no ve esa deuda
        getCon(datos.token(datos.vendedor2), "/api/fiado/clientes/{id}/recordatorio-whatsapp", cliente)
                .andExpect(status().isUnprocessableContent());
    }
}
