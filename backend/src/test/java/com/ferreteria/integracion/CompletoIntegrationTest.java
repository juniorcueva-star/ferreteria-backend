package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pedido del dueno: vender "completos" (Ej: perno + tuerca + arandela) a precio propio. Al venderlo se descuenta
 * cada pieza; al anular vuelve exactamente lo que salio.
 */
class CompletoIntegrationTest extends IntegracionTestBase {

    private Producto perno;
    private Producto tuerca;
    private Producto arandela;
    private String admin;
    private String vendedor;

    @BeforeEach
    void preparar() throws Exception {
        perno = datos.producto("PER-38", UnidadBase.UNIDAD, "0.50");
        tuerca = datos.producto("TUE-38", UnidadBase.UNIDAD, "0.20");
        arandela = datos.producto("ARA-38", UnidadBase.UNIDAD, "0.10");
        admin = datos.token(datos.admin);
        vendedor = datos.token(datos.vendedor1);
        cargarStock(perno, datos.tienda1, 100);
        cargarStock(tuerca, datos.tienda1, 50);
        cargarStock(arandela, datos.tienda1, 30);
    }

    private Map<String, Object> completo(String nombre, Object precio, Producto... piezas) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("precioVenta", precio);
        cuerpo.put("componentes", java.util.Arrays.stream(piezas)
                .map(p -> Map.of("presentacionId", datos.presentacionId(p, 0), "cantidad", 1)).toList());
        return cuerpo;
    }

    private Long crearCompleto() throws Exception {
        return leerId(postCon(admin, "/api/productos/{id}/completos", completo("Completo", 0.70, perno, tuerca, arandela),
                perno.getId()).andExpect(status().isCreated()).andReturn(), "$.id");
    }

    private Map<String, Object> ventaDeCompletos(Long completoId, int cantidad, Object pago) {
        return Map.of("condicion", "CONTADO",
                "detalles", List.of(Map.of("completoId", completoId, "cantidad", cantidad)),
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", pago)));
    }

    @Test
    @DisplayName("El ADMIN crea completos: precio sugerido = suma de piezas, aparecen en el producto y se validan")
    void crear() throws Exception {
        postCon(admin, "/api/productos/{id}/completos", completo("Con tuerca", null, perno, tuerca), perno.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.precioVenta").value(0.70))
                .andExpect(jsonPath("$.precioSugerido").value(0.70))
                .andExpect(jsonPath("$.composicion").value(containsString(" + ")))
                .andExpect(jsonPath("$.componentes.length()").value(2));
        crearCompleto();
        getCon(vendedor, "/api/productos/{id}", perno.getId())
                .andExpect(jsonPath("$.completos.length()").value(2))
                .andExpect(jsonPath("$.completos[1].nombre").value("Completo"))
                .andExpect(jsonPath("$.completos[1].precioSugerido").value(0.80));

        postCon(admin, "/api/productos/{id}/completos", completo("completo", 1, perno), perno.getId())
                .andExpect(status().isConflict());
        Map<String, Object> repetida = completo("Doble", 1, perno, perno);
        postCon(admin, "/api/productos/{id}/completos", repetida, perno.getId())
                .andExpect(status().isUnprocessableContent());
        Map<String, Object> fraccion = completo("Media", 1, perno);
        fraccion.put("componentes", List.of(Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 0.5)));
        postCon(admin, "/api/productos/{id}/completos", fraccion, perno.getId())
                .andExpect(status().isUnprocessableContent());
        postCon(vendedor, "/api/productos/{id}/completos", completo("Otro", 1, perno), perno.getId())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vender 10 completos descuenta 10 de cada pieza; anular devuelve lo que salio aunque el completo cambie")
    void venderYAnular() throws Exception {
        Long completoId = crearCompleto();
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        Long ventaId = leerId(postCon(vendedor, "/api/ventas", ventaDeCompletos(completoId, 10, 7))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(7.0))
                .andExpect(jsonPath("$.detalles[0].completoId").value(completoId))
                .andExpect(jsonPath("$.detalles[0].presentacionId").doesNotExist())
                .andExpect(jsonPath("$.detalles[0].descripcion").value(containsString("Completo")))
                .andExpect(jsonPath("$.detalles[0].composicion").value(containsString("Producto TUE-38")))
                .andReturn(), "$.id");
        assertThat(datos.stock(perno, datos.tienda1)).isEqualByComparingTo("90");
        assertThat(datos.stock(tuerca, datos.tienda1)).isEqualByComparingTo("40");
        assertThat(datos.stock(arandela, datos.tienda1)).isEqualByComparingTo("20");

        byte[] pdf = getCon(vendedor, "/api/ventas/{id}/pdf", ventaId).andReturn().getResponse().getContentAsByteArray();
        try (PdfReader lector = new PdfReader(pdf)) {
            assertThat(new PdfTextExtractor(lector).getTextFromPage(1)).contains("Completo", "Incluye:");
        }

        // El ADMIN cambia el completo (ya no lleva arandela); la anulacion devuelve lo que salio en la venta
        putCon(admin, "/api/productos/{id}/completos/{c}", completo("Completo", 0.70, perno, tuerca),
                perno.getId(), completoId).andExpect(status().isOk());
        postCon(vendedor, "/api/ventas/{id}/anular", Map.of("motivo", "Prueba de anulacion"), ventaId)
                .andExpect(status().isOk());
        assertThat(datos.stock(perno, datos.tienda1)).isEqualByComparingTo("100");
        assertThat(datos.stock(tuerca, datos.tienda1)).isEqualByComparingTo("50");
        assertThat(datos.stock(arandela, datos.tienda1)).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("Sin stock de una pieza, fracciones o completo inactivo: no se vende y no cambia el stock")
    void reglas() throws Exception {
        Long completoId = crearCompleto();
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        postCon(vendedor, "/api/ventas", ventaDeCompletos(completoId, 31, 21.70))
                .andExpect(status().isConflict()) // stock insuficiente (mismo codigo que una venta normal)
                .andExpect(jsonPath("$.mensaje").value(containsString("ARA-38")));
        Map<String, Object> fraccion = Map.of("condicion", "CONTADO",
                "detalles", List.of(Map.of("completoId", completoId, "cantidad", 1.5)),
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 2)));
        postCon(vendedor, "/api/ventas", fraccion).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value(containsString("unidades enteras")));
        Map<String, Object> inactivo = completo("Completo", 0.70, perno, tuerca, arandela);
        inactivo.put("activo", false);
        putCon(admin, "/api/productos/{id}/completos/{c}", inactivo, perno.getId(), completoId).andExpect(status().isOk());
        postCon(vendedor, "/api/ventas", ventaDeCompletos(completoId, 1, 1))
                .andExpect(status().isUnprocessableContent());
        assertThat(datos.stock(perno, datos.tienda1)).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Cotizar completos con precio editado y convertir la cotizacion en venta")
    void cotizarYConvertir() throws Exception {
        Long completoId = crearCompleto();
        Long cotizacionId = leerId(postCon(vendedor, "/api/cotizaciones", Map.of("detalles", List.of(
                        Map.of("completoId", completoId, "cantidad", 20, "precioUnitario", 0.65),
                        Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(15.50)) // 20 x 0.65 + 5 x 0.50
                .andExpect(jsonPath("$.detalles[0].completoId").value(completoId))
                .andExpect(jsonPath("$.detalles[0].decimales").value(0))
                .andReturn(), "$.id");
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        postCon(vendedor, "/api/cotizaciones/{id}/convertir-venta", Map.of("condicion", "CONTADO",
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 15.50))), cotizacionId)
                .andExpect(status().isOk());
        assertThat(datos.stock(perno, datos.tienda1)).isEqualByComparingTo("75"); // 100 - 20 - 5
        assertThat(datos.stock(tuerca, datos.tienda1)).isEqualByComparingTo("30");
        assertThat(datos.stock(arandela, datos.tienda1)).isEqualByComparingTo("10");
    }
}
