package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ofertas: precio rebajado entre dos fechas que la venta y la cotizacion cobran mientras esta vigente. */
class OfertaIntegrationTest extends IntegracionTestBase {

    private Producto lija;
    private Long unidad;
    private String admin;
    private String vendedor;

    @BeforeEach
    void preparar() throws Exception {
        lija = datos.producto("OFE-1", UnidadBase.UNIDAD, "0.50");
        unidad = datos.presentacionId(lija, 0);
        cargarStock(lija, datos.tienda1, 1000);
        admin = datos.token(datos.admin);
        vendedor = datos.token(datos.vendedor1);
    }

    private String oferta() {
        return "/api/productos/" + lija.getId() + "/presentaciones/" + unidad + "/oferta";
    }

    private static Map<String, Object> datosOferta(double precio, LocalDate desde, LocalDate hasta) {
        return Map.of("precio", precio, "desde", desde.toString(), "hasta", hasta.toString());
    }

    private void vender(int cantidad, double precioEsperado, String tipoEsperado) throws Exception {
        // En efectivo se puede pagar de mas: el resto es vuelto
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO",
                        "detalles", List.of(Map.of("presentacionId", unidad, "cantidad", cantidad)),
                        "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 100))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(precioEsperado))
                .andExpect(jsonPath("$.detalles[0].tipoPrecio").value(tipoEsperado));
    }

    @Test
    @DisplayName("El ADMIN pone una oferta valida; se rechazan precios mayores, fechas pasadas y al VENDEDOR")
    void definirOferta() throws Exception {
        LocalDate hoy = hoyLima();
        putCon(admin, oferta(), datosOferta(0.45, hoy, hoy.plusDays(7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentaciones[0].precioOferta").value(0.45))
                .andExpect(jsonPath("$.presentaciones[0].ofertaHasta").value(hoy.plusDays(7).toString()));
        getCon(admin, "/api/productos?enOferta=true&texto=OFE-1")
                .andExpect(jsonPath("$.contenido.length()").value(1));

        putCon(admin, oferta(), datosOferta(0.50, hoy, hoy.plusDays(7))).andExpect(status().isUnprocessableContent());
        putCon(admin, oferta(), datosOferta(0.45, hoy.minusDays(9), hoy.minusDays(1)))
                .andExpect(status().isUnprocessableContent());
        putCon(admin, oferta(), datosOferta(0.45, hoy.plusDays(3), hoy)).andExpect(status().isUnprocessableContent());
        putCon(vendedor, oferta(), datosOferta(0.45, hoy, hoy)).andExpect(status().isForbidden());

        deleteCon(admin, oferta())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentaciones[0].precioOferta").doesNotExist());
        getCon(admin, "/api/productos?enOferta=true&texto=OFE-1")
                .andExpect(jsonPath("$.contenido.length()").value(0));
    }

    @Test
    @DisplayName("La venta cobra la oferta vigente; el precio por mayor gana si es menor y una oferta futura no aplica")
    void aplicarOferta() throws Exception {
        LocalDate hoy = hoyLima();
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        putCon(admin, "/api/productos/" + lija.getId() + "/presentaciones/" + unidad + "/escalas",
                Map.of("escalas", List.of(Map.of("cantidadMinima", 100, "precio", 0.40)))).andExpect(status().isOk());

        putCon(admin, oferta(), datosOferta(0.45, hoy, hoy)).andExpect(status().isOk());
        vender(10, 0.45, "OFERTA");
        vender(120, 0.40, "MAYOR");
        postCon(vendedor, "/api/cotizaciones", Map.of("detalles",
                        List.of(Map.of("presentacionId", unidad, "cantidad", 5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(0.45))
                .andExpect(jsonPath("$.detalles[0].tipoPrecio").value("OFERTA"));

        putCon(admin, oferta(), datosOferta(0.45, hoy.plusDays(1), hoy.plusDays(5))).andExpect(status().isOk());
        vender(10, 0.50, "LISTA");
    }
}
