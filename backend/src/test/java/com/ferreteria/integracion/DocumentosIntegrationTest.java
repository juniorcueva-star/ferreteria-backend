package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pedido del dueno: la cotizacion y la nota de venta impresas llevan el logo, el nombre comercial, el lema, el
 * telefono y las cuentas bancarias de la empresa.
 */
class DocumentosIntegrationTest extends IntegracionTestBase {

    private Producto perno;
    private String admin;
    private String vendedor;

    @BeforeEach
    void preparar() throws Exception {
        perno = datos.producto("PER-1", UnidadBase.UNIDAD, "0.50");
        admin = datos.token(datos.admin);
        vendedor = datos.token(datos.vendedor1);
        Map<String, Object> empresa = new HashMap<>(Map.of(
                "ruc", "20111111111",
                "razonSocial", "Ferreteria Uno S.A.C.",
                "nombreComercial", "Bonifaz & Cia",
                "lema", "Pernos y afines",
                "telefono", "950 104 503",
                "datosBancarios", "BBVA: 0011-0814-0219320979\nC.C.I.: 011-814-000219320979-14"));
        putCon(admin, "/api/empresas/{id}", empresa, datos.empresa1.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lema").value("Pernos y afines"))
                .andExpect(jsonPath("$.telefono").value("950 104 503"))
                .andExpect(jsonPath("$.datosBancarios").value(containsString("C.C.I.")));
    }

    @Test
    @DisplayName("Los campos de impresion vacios se guardan como null")
    void camposVaciosSonNull() throws Exception {
        Map<String, Object> empresa = Map.of("ruc", "20222222222", "razonSocial", "Ferreteria Dos S.A.C.",
                "telefono", "  ", "lema", "", "datosBancarios", " ");
        putCon(admin, "/api/empresas/{id}", empresa, datos.empresa2.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value(nullValue()))
                .andExpect(jsonPath("$.lema").value(nullValue()))
                .andExpect(jsonPath("$.datosBancarios").value(nullValue()));
    }

    @Test
    @DisplayName("La nota de venta en PDF lleva el membrete, el detalle, el pago y las cuentas bancarias")
    void notaDeVentaPdf() throws Exception {
        cargarStock(perno, datos.tienda1, 50);
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        Long ventaId = leerId(postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO",
                        "detalles", List.of(Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 10)),
                        "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 5))))
                .andExpect(status().isCreated()).andReturn(), "$.id");

        byte[] pdf = getCon(vendedor, "/api/ventas/{id}/pdf", ventaId)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andReturn().getResponse().getContentAsByteArray();
        String texto = texto(pdf);
        assertThat(texto).contains("Bonifaz & Cia", "PERNOS Y AFINES", "R.U.C. 20111111111", "NOTA DE VENTA",
                "950 104 503", "Cuentas para depósito", "0011-0814-0219320979", "S/ 5.00");
        assertThat(texto).containsPattern("NV\\d{2}-000001");

        getCon(vendedor, "/api/ventas/{id}/pdf?descargar=true", ventaId)
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));
        // Otra tienda no puede ver la nota de venta
        getCon(datos.token(datos.vendedor2), "/api/ventas/{id}/pdf", ventaId).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("La cotizacion en PDF usa el mismo membrete")
    void cotizacionPdf() throws Exception {
        Long id = leerId(postCon(vendedor, "/api/cotizaciones", Map.of("detalles",
                        List.of(Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 4))))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        byte[] pdf = getCon(vendedor, "/api/cotizaciones/{id}/pdf", id).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(texto(pdf)).contains("Bonifaz & Cia", "COTIZACIÓN", "COT-", "Cuentas para depósito", "S/ 2.00");
    }

    @Test
    @DisplayName("Ticket de 80 mm: ancho de tiquetera, membrete, productos, total y pago")
    void ticket() throws Exception {
        cargarStock(perno, datos.tienda1, 50);
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        Long ventaId = leerId(postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO",
                        "detalles", List.of(Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 6)),
                        "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 3))))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        byte[] pdf = getCon(vendedor, "/api/ventas/{id}/ticket", ventaId)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        try (PdfReader lector = new PdfReader(pdf)) {
            assertThat(lector.getPageSize(1).getWidth()).isBetween(226f, 227f); // 80 mm
            assertThat(new PdfTextExtractor(lector).getTextFromPage(1))
                    .contains("Bonifaz & Cia", "R.U.C. 20111111111", "NOTA DE VENTA", "6 x S/ 0.50", "S/ 3.00", "Efectivo");
        }
        getCon(datos.token(datos.vendedor2), "/api/ventas/{id}/ticket", ventaId).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Guia de traslado: origen, destino, productos con su ubicacion en el almacen y firmas")
    void guiaDeTraslado() throws Exception {
        String almacenero = datos.token(datos.almacenero);
        cargarStock(perno, datos.almacen, 100);
        putCon(almacenero, "/api/inventario/stock/ubicacion-fisica", Map.of("productoId", perno.getId(),
                "ubicacionFisica", "Pasillo 3 - Estante B")).andExpect(status().isOk());
        Long trasladoId = leerId(postCon(almacenero, "/api/traslados", Map.of("destinoId", datos.tienda1.getId(),
                        "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 40))))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        byte[] pdf = getCon(almacenero, "/api/traslados/{id}/guia", trasladoId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(texto(pdf)).contains("GUÍA DE TRASLADO", "TR-", "Almacen Central", "Tienda 1", "PER-1",
                "40", "Pasillo 3 - Estante B", "Entregué conforme", "Recibí conforme");
        getCon(vendedor, "/api/traslados/{id}/guia", trasladoId).andExpect(status().isOk());
        getCon(datos.token(datos.vendedor2), "/api/traslados/{id}/guia", trasladoId).andExpect(status().isForbidden());
    }

    private static String texto(byte[] pdf) throws IOException {
        try (PdfReader lector = new PdfReader(pdf)) {
            return new PdfTextExtractor(lector).getTextFromPage(1);
        }
    }
}
