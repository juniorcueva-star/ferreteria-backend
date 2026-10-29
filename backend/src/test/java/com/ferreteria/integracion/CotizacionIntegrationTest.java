package com.ferreteria.integracion;

import com.ferreteria.entity.Cliente;
import com.ferreteria.entity.Producto;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CotizacionIntegrationTest extends IntegracionCotizacionTestBase {

    @Test
    void creaLeeYListaUnaCotizacionConFraccionAjustesYSinEfectosComerciales() throws Exception {
        Cliente cliente = cliente("Cliente cotizacion");
        Producto perno = producto("PER-COT", "1.00", "Ciento", "100", "80.00");
        Long cientoId = perno.getPresentaciones().get(1).getId();
        long comercialInicial = estadoComercial();

        MvcResult creada = postCon(token(vendedor1), "/api/cotizaciones", Map.of(
                "clienteId", cliente.getId(), "observacion", "  Obra norte  ", "detalles", List.of(
                        Map.of("presentacionId", cientoId, "cantidad", 0.5, "ajustePorcentaje", -10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(matchesPattern("COT-[A-F0-9]{8}")))
                .andExpect(jsonPath("$.ubicacionId").value(tienda1.getId()))
                .andExpect(jsonPath("$.usuario").value("vendedor-cot-1"))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.moneda").value("PEN"))
                .andExpect(jsonPath("$.observacion").value("Obra norte"))
                .andExpect(jsonPath("$.total").value(36.00))
                .andExpect(jsonPath("$.subtotal").value(30.51))
                .andExpect(jsonPath("$.igv").value(5.49))
                .andExpect(jsonPath("$.descuento").value(4.00))
                .andExpect(jsonPath("$.detalles[0].cantidadBase").value(50))
                .andExpect(jsonPath("$.detalles[0].factor").value(100))
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(80.00))
                .andExpect(jsonPath("$.detalles[0].descuento").value(4.00))
                .andExpect(jsonPath("$.detalles[0].ajustePorcentaje").value(-10))
                .andReturn();
        Long cotizacionId = id(creada);

        getCon(token(vendedor1), "/api/cotizaciones/{id}", cotizacionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles.length()").value(1));
        getCon(token(vendedor1), "/api/cotizaciones?estado=BORRADOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].detalles.length()").value(0));

        assertThat(estadoComercial()).isEqualTo(comercialInicial);
    }

    @Test
    void editarConservaSnapshotYEmitirOAnularNoAfectaInventarioNiVentas() throws Exception {
        Cliente cliente = cliente("Cliente snapshot");
        Producto perno = producto("SNAP-COT", "1.00", "Millar", "1000", "70.00");
        Long millarId = perno.getPresentaciones().get(1).getId();
        Long cotizacionId = id(postCon(token(vendedor1), "/api/cotizaciones", Map.of("clienteId", cliente.getId(),
                "detalles", List.of(Map.of("presentacionId", millarId, "cantidad", 0.5)))).andReturn());
        long comercialInicial = estadoComercial();
        jdbc.update("update presentacion set factor = ?, precio_venta = ? where id = ?", 900, 60, millarId);

        putCon(token(vendedor1), "/api/cotizaciones/{id}", Map.of("clienteId", cliente.getId(),
                "detalles", List.of(Map.of("presentacionId", millarId, "cantidad", 2))), cotizacionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles[0].factor").value(1000))
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(70.00))
                .andExpect(jsonPath("$.detalles[0].cantidadBase").value(2000))
                .andExpect(jsonPath("$.total").value(140.00));

        postCon(token(vendedor1), "/api/cotizaciones/{id}/emitir", Map.of(), cotizacionId)
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EMITIDA"));
        putCon(token(vendedor1), "/api/cotizaciones/{id}", Map.of("clienteId", cliente.getId(),
                "detalles", List.of(Map.of("presentacionId", millarId, "cantidad", 1))), cotizacionId)
                .andExpect(status().isUnprocessableContent());
        postCon(token(vendedor1), "/api/cotizaciones/{id}/anular", Map.of(), cotizacionId)
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ANULADA"));
        postCon(token(vendedor1), "/api/cotizaciones/{id}/anular", Map.of(), cotizacionId)
                .andExpect(status().isUnprocessableContent());

        assertThat(estadoComercial()).isEqualTo(comercialInicial);
    }

    @Test
    void seguridadPorRolYUbicacionSeAplicaEnTodosLosContratos() throws Exception {
        Cliente cliente = cliente("Cliente acceso");
        Producto perno = producto("ACC-COT", "3.00", "Docena", "12", "30.00");
        Long presentacionId = perno.getPresentaciones().get(1).getId();
        Map<String, Object> solicitud = Map.of("clienteId", cliente.getId(),
                "detalles", List.of(Map.of("presentacionId", presentacionId, "cantidad", 1)));

        mockMvc.perform(get("/api/cotizaciones")).andExpect(status().isUnauthorized());
        postCon(token(almacenero), "/api/cotizaciones", solicitud).andExpect(status().isForbidden());
        Long cotizacionId = id(postCon(token(vendedor1), "/api/cotizaciones", solicitud).andReturn());
        getCon(token(vendedor2), "/api/cotizaciones/{id}", cotizacionId).andExpect(status().isForbidden());
        getCon(token(vendedor2), "/api/cotizaciones?ubicacionId={id}", tienda1.getId()).andExpect(status().isForbidden());
        getCon(token(vendedor2), "/api/cotizaciones").andExpect(jsonPath("$.totalElementos").value(0));
        getCon(token(admin), "/api/cotizaciones?ubicacionId={id}", tienda1.getId())
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        postCon(token(admin), "/api/cotizaciones", solicitud).andExpect(status().isUnprocessableContent());
        postCon(token(admin), "/api/cotizaciones", Map.of("ubicacionId", tienda2.getId(), "clienteId", cliente.getId(),
                "detalles", List.of(Map.of("presentacionId", presentacionId, "cantidad", 1))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ubicacionId").value(tienda2.getId()));
    }

    @Test
    void rechazaAjustesInvalidosPresentacionesRepetidasYOrdenamientosNoPermitidos() throws Exception {
        Cliente cliente = cliente("Cliente reglas");
        Producto perno = producto("REG-COT", "2.00", "Paquete", "10", "18.00");
        Long presentacionId = perno.getPresentaciones().get(1).getId();

        postCon(token(vendedor1), "/api/cotizaciones", Map.of("clienteId", cliente.getId(), "detalles", List.of(
                Map.of("presentacionId", presentacionId, "cantidad", 1, "descuento", 1, "ajustePorcentaje", -5))))
                .andExpect(status().isUnprocessableContent());
        postCon(token(vendedor1), "/api/cotizaciones", Map.of("clienteId", cliente.getId(), "detalles", List.of(
                Map.of("presentacionId", presentacionId, "cantidad", 1),
                Map.of("presentacionId", presentacionId, "cantidad", 2))))
                .andExpect(status().isUnprocessableContent());
        getCon(token(vendedor1), "/api/cotizaciones?sort=detalles.subtotal").andExpect(status().isBadRequest());
    }
}
