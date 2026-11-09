package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.repository.CotizacionRepository;
import com.ferreteria.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Punto 2 del encargado: cotizaciones con total e IGV, conversion en nota de venta, PDF, WhatsApp y correo.
 */
class CotizacionIntegrationTest extends IntegracionTestBase {

    @Autowired private CotizacionRepository cotizacionRepository;
    @Autowired private VentaRepository ventaRepository;

    private Producto perno;
    private String vendedor;

    @BeforeEach
    void preparar() {
        perno = datos.producto("PER-1", UnidadBase.UNIDAD, "0.50", "Docena", "12", "5.50");
        vendedor = datos.token(datos.vendedor1);
    }

    /** 10 unidades a S/ 0.50 = 5.00 + 2 docenas a S/ 5.00 (precio editado) - 1.00 de descuento = 9.00 -> 14.00 */
    private Map<String, Object> cotizacion() {
        return new HashMap<>(Map.of("observacion", "Entrega en obra",
                "detalles", List.of(
                        Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 10),
                        Map.of("presentacionId", datos.presentacionId(perno, 1), "cantidad", 2, "precioUnitario", 5,
                                "descuento", 1))));
    }

    private Long crear() throws Exception {
        return leerId(postCon(vendedor, "/api/cotizaciones", cotizacion()).andExpect(status().isCreated()).andReturn(), "$.id");
    }

    @Test
    @DisplayName("Crea la cotizacion en BORRADOR con total = subtotal + IGV y precio editable por linea")
    void crearConIgv() throws Exception {
        postCon(vendedor, "/api/cotizaciones", cotizacion())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(matchesPattern("COT-\\d{6}")))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.empresaRuc").value("20111111111"))
                .andExpect(jsonPath("$.total").value(14.0))
                .andExpect(jsonPath("$.subtotal").value(11.86))
                .andExpect(jsonPath("$.igv").value(2.14))
                .andExpect(jsonPath("$.descuento").value(1.0))
                .andExpect(jsonPath("$.detalles[1].precioUnitario").value(5.0))
                .andExpect(jsonPath("$.detalles[1].subtotal").value(9.0))
                .andExpect(jsonPath("$.fechaValidez").value(hoyLima().plusDays(7).toString()));
    }

    @Test
    @DisplayName("Valida decimales, edita solo en BORRADOR y no permite presentaciones repetidas")
    void reglasDeEdicion() throws Exception {
        Map<String, Object> fraccion = cotizacion();
        fraccion.put("detalles", List.of(Map.of("presentacionId", datos.presentacionId(perno, 1), "cantidad", 1.5)));
        postCon(vendedor, "/api/cotizaciones", fraccion)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value(containsString("no acepta fracciones")));

        Long id = crear();
        Map<String, Object> editada = cotizacion();
        editada.put("detalles", List.of(Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 20)));
        putCon(vendedor, "/api/cotizaciones/{id}", editada, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles.length()").value(1))
                .andExpect(jsonPath("$.total").value(10.0));

        Map<String, Object> repetida = cotizacion();
        repetida.put("detalles", List.of(Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 1),
                Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 2)));
        putCon(vendedor, "/api/cotizaciones/{id}", repetida, id).andExpect(status().isUnprocessableContent());

        postCon(vendedor, "/api/cotizaciones/{id}/emitir", Map.of(), id)
                .andExpect(jsonPath("$.estado").value("EMITIDA"));
        putCon(vendedor, "/api/cotizaciones/{id}", editada, id).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Convertir crea la nota de venta con el mismo total, descuenta stock y no se repite")
    void convertirEnVenta() throws Exception {
        Long id = crear();
        cargarStock(perno, datos.tienda1, 100);
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        Map<String, Object> contado = Map.of("condicion", "CONTADO",
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 14)));

        Long ventaId = leerId(postCon(vendedor, "/api/cotizaciones/{id}/convertir-venta", contado, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EMITIDA"))
                .andExpect(jsonPath("$.ventaNumero").value(matchesPattern("NV\\d{2}-\\d{6}")))
                .andReturn(), "$.ventaId");
        getCon(vendedor, "/api/ventas/{id}", ventaId)
                .andExpect(jsonPath("$.tipoDocumento").value("NOTA_VENTA"))
                .andExpect(jsonPath("$.total").value(14.0))
                .andExpect(jsonPath("$.igv").value(2.14));
        assertThat(datos.stock(perno, datos.tienda1)).isEqualByComparingTo("66"); // 10 + 2 x 12

        postCon(vendedor, "/api/cotizaciones/{id}/convertir-venta", contado, id)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value(containsString("ya se convirtio")));
        postCon(vendedor, "/api/cotizaciones/{id}/anular", Map.of("motivo", "Prueba de anulacion"), id)
                .andExpect(status().isUnprocessableContent());
        assertThat(ventaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sin stock la conversion se rechaza y la cotizacion sigue sin convertir")
    void convertirSinStock() throws Exception {
        Long id = crear();
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        postCon(vendedor, "/api/cotizaciones/{id}/convertir-venta", Map.of("condicion", "CONTADO",
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 14))), id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"));
        getCon(vendedor, "/api/cotizaciones/{id}", id).andExpect(jsonPath("$.ventaId").doesNotExist());
    }

    @Test
    @DisplayName("PDF valido para imprimir o descargar, y enlace publico por token sin iniciar sesion")
    void pdf() throws Exception {
        Long id = crear();
        MvcResult resultado = getCon(vendedor, "/api/cotizaciones/{id}/pdf", id)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline")))
                .andReturn();
        byte[] pdf = resultado.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        getCon(vendedor, "/api/cotizaciones/{id}/pdf?descargar=true", id)
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment")));

        String token = cotizacionRepository.findById(id).orElseThrow().getTokenPublico();
        byte[] publico = mockMvc.perform(get("/api/publico/cotizaciones/{t}/pdf", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(publico, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        mockMvc.perform(get("/api/publico/cotizaciones/{t}/pdf", "token-falso")).andExpect(status().isNotFound());

        postCon(vendedor, "/api/cotizaciones/{id}/anular", Map.of("motivo", "Cliente no respondio"), id)
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/publico/cotizaciones/{t}/pdf", token)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("WhatsApp: enlace wa.me con el resumen codificado y el enlace de descarga; valida el telefono")
    void whatsapp() throws Exception {
        Long id = crear();
        String token = cotizacionRepository.findById(id).orElseThrow().getTokenPublico();
        getCon(vendedor, "/api/cotizaciones/{id}/enlace-whatsapp?telefono=987 654 321", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith("https://wa.me/51987654321?text=Hola%2C%20le%20enviamos")))
                .andExpect(jsonPath("$.url").value(containsString("Total%3A%20S%2F%2014.00")))
                .andExpect(jsonPath("$.mensaje").value(containsString("/api/publico/cotizaciones/" + token + "/pdf")))
                .andExpect(jsonPath("$.mensaje").value(containsString("Válida hasta")));
        getCon(vendedor, "/api/cotizaciones/{id}", id).andExpect(jsonPath("$.estado").value("EMITIDA"));
        getCon(vendedor, "/api/cotizaciones/{id}/enlace-whatsapp?telefono=12345", id)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Sin servidor de correo configurado, enviar responde 422 claro y no cambia la cotizacion")
    void correoSinConfiguracion() throws Exception {
        Long id = crear();
        postCon(vendedor, "/api/cotizaciones/{id}/enviar-correo", Map.of("destinatario", "cliente@correo.pe"), id)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value(containsString("MAIL_HOST")));
        getCon(vendedor, "/api/cotizaciones/{id}", id).andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    @Test
    @DisplayName("Un vendedor de otra tienda no ve la cotizacion; el almacenero no tiene acceso")
    void accesoPorTienda() throws Exception {
        Long id = crear();
        getCon(datos.token(datos.vendedor2), "/api/cotizaciones/{id}", id).andExpect(status().isForbidden());
        getCon(datos.token(datos.vendedor2), "/api/cotizaciones/{id}/pdf", id).andExpect(status().isForbidden());
        getCon(datos.token(datos.vendedor2), "/api/cotizaciones").andExpect(jsonPath("$.totalElementos").value(0));
        getCon(datos.token(datos.almacenero), "/api/cotizaciones").andExpect(status().isForbidden());
    }
}
