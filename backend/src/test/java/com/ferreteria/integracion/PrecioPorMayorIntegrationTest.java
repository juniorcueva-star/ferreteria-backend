package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Precio por mayor: escalas por cantidad que el sistema aplica solo en la venta y la cotizacion. */
class PrecioPorMayorIntegrationTest extends IntegracionTestBase {

    private Producto perno;
    private Long unidad;
    private String admin;
    private String vendedor;

    @BeforeEach
    void preparar() throws Exception {
        perno = datos.producto("MAY-1", UnidadBase.UNIDAD, "0.50");
        unidad = datos.presentacionId(perno, 0);
        cargarStock(perno, datos.tienda1, 1000);
        admin = datos.token(datos.admin);
        vendedor = datos.token(datos.vendedor1);
    }

    private String escalas() {
        return "/api/productos/" + perno.getId() + "/presentaciones/" + unidad + "/escalas";
    }

    @Test
    @DisplayName("El ADMIN define escalas ordenadas; se rechazan precios que no bajan, fracciones y al VENDEDOR")
    void definirEscalas() throws Exception {
        putCon(admin, escalas(), Map.of("escalas", List.of(
                        Map.of("cantidadMinima", 500, "precio", 0.35),
                        Map.of("cantidadMinima", 100, "precio", 0.40))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentaciones[0].escalas.length()").value(2))
                .andExpect(jsonPath("$.presentaciones[0].escalas[0].cantidadMinima").value(100.0))
                .andExpect(jsonPath("$.presentaciones[0].escalas[1].precio").value(0.35));
        getCon(admin, "/api/auditoria?entidad=PRODUCTO&texto=precio por mayor")
                .andExpect(jsonPath("$.contenido[0].descripcion").value(containsString("desde 100 a S/ 0.40")));

        putCon(admin, escalas(), Map.of("escalas", List.of(Map.of("cantidadMinima", 100, "precio", 0.50))))
                .andExpect(status().isUnprocessableContent());
        putCon(admin, escalas(), Map.of("escalas", List.of(
                        Map.of("cantidadMinima", 100, "precio", 0.40),
                        Map.of("cantidadMinima", 500, "precio", 0.45))))
                .andExpect(status().isUnprocessableContent());
        putCon(admin, escalas(), Map.of("escalas", List.of(Map.of("cantidadMinima", 100.5, "precio", 0.40))))
                .andExpect(status().isUnprocessableContent());
        putCon(admin, escalas(), Map.of("escalas", List.of(Map.of("cantidadMinima", 1, "precio", 0.40))))
                .andExpect(status().isBadRequest());
        putCon(vendedor, escalas(), Map.of("escalas", List.of())).andExpect(status().isForbidden());

        // Se pueden volver a guardar las mismas cantidades y quitar todas
        putCon(admin, escalas(), Map.of("escalas", List.of(Map.of("cantidadMinima", 100, "precio", 0.42))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentaciones[0].escalas[0].precio").value(0.42));
        putCon(admin, escalas(), Map.of("escalas", List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentaciones[0].escalas.length()").value(0));
    }

    @Test
    @DisplayName("La venta y la cotizacion cobran la escala que alcanza la cantidad y la marcan como precio por mayor")
    void aplicarEnVentaYCotizacion() throws Exception {
        putCon(admin, escalas(), Map.of("escalas", List.of(
                Map.of("cantidadMinima", 100, "precio", 0.40),
                Map.of("cantidadMinima", 500, "precio", 0.35)))).andExpect(status().isOk());
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());

        postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO",
                        "detalles", List.of(Map.of("presentacionId", unidad, "cantidad", 120)),
                        "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 48))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(0.40))
                .andExpect(jsonPath("$.detalles[0].tipoPrecio").value("MAYOR"))
                .andExpect(jsonPath("$.total").value(48.0));
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO",
                        "detalles", List.of(Map.of("presentacionId", unidad, "cantidad", 10)),
                        "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(0.50))
                .andExpect(jsonPath("$.detalles[0].tipoPrecio").value("LISTA"));

        postCon(vendedor, "/api/cotizaciones", Map.of("detalles",
                        List.of(Map.of("presentacionId", unidad, "cantidad", 600))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(0.35))
                .andExpect(jsonPath("$.detalles[0].tipoPrecio").value("MAYOR"))
                .andExpect(jsonPath("$.total").value(210.0));
        // Un precio escrito a mano en la cotizacion no es precio por mayor
        postCon(vendedor, "/api/cotizaciones", Map.of("detalles",
                        List.of(Map.of("presentacionId", unidad, "cantidad", 600, "precioUnitario", 0.30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detalles[0].tipoPrecio").value("LISTA"));
    }
}
