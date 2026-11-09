package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.repository.EncargoRepository;
import com.ferreteria.repository.MovimientoInventarioRepository;
import com.ferreteria.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Punto 3 del encargado: encargos de productos sin stock. Solo la entrega mueve el stock.
 */
class EncargoIntegrationTest extends IntegracionTestBase {

    @Autowired private MovimientoInventarioRepository movimientoRepository;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private EncargoRepository encargoRepository;

    private Producto taladro;
    private String vendedor;

    @BeforeEach
    void preparar() {
        taladro = datos.producto("TAL-1", UnidadBase.UNIDAD, "100.00", "Docena", "12", "1100.00");
        vendedor = datos.token(datos.vendedor1);
    }

    private Map<String, Object> encargo(Number cantidad, Number precio) {
        return new HashMap<>(Map.of("presentacionId", datos.presentacionId(taladro, 0), "cantidad", cantidad,
                "precioUnitario", precio, "observacion", "Lo trae el proveedor el viernes"));
    }

    private Long crearEncargo() throws Exception {
        return leerId(postCon(vendedor, "/api/encargos", encargo(2, 115)).andReturn(), "$.id");
    }

    private Map<String, Object> entregaContado(Number monto) {
        return Map.of("condicion", "CONTADO", "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", monto)));
    }

    @Test
    @DisplayName("Crear un encargo no toca el stock y muestra el recargo sobre el precio de lista")
    void crearNoTocaStock() throws Exception {
        long movimientos = movimientoRepository.count();
        postCon(vendedor, "/api/encargos", encargo(2, 115))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(org.hamcrest.Matchers.matchesPattern("EN-\\d{6}")))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.ubicacionNombre").value("Tienda 1"))
                .andExpect(jsonPath("$.total").value(230.0))
                .andExpect(jsonPath("$.precioLista").value(100.0))
                .andExpect(jsonPath("$.recargoPorcentaje").value(15.0));
        assertThat(datos.stock(taladro, datos.tienda1)).isEqualByComparingTo("0");
        assertThat(movimientoRepository.count()).isEqualTo(movimientos);
    }

    @Test
    @DisplayName("El precio no puede ser menor que el costo; la cantidad respeta los decimales de la presentacion")
    void reglasDePrecioYDecimales() throws Exception {
        Map<String, Object> barato = encargo(1, 90);
        barato.put("costoReferencia", 95);
        postCon(vendedor, "/api/encargos", barato)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("no puede ser menor que su costo")));
        Map<String, Object> fraccion = encargo(1.5, 120);
        fraccion.put("presentacionId", datos.presentacionId(taladro, 1));
        postCon(vendedor, "/api/encargos", fraccion).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Entregar sin stock responde 422 y no cambia nada")
    void entregarSinStock() throws Exception {
        Long id = crearEncargo();
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        postCon(vendedor, "/api/encargos/{id}/entregar", entregaContado(230), id)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"))
                .andExpect(jsonPath("$.detalle[0]").value("disponible: 0.000"));
        assertThat(ventaRepository.count()).isZero();
        getCon(vendedor, "/api/encargos/{id}", id).andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("Entregar crea la venta con el precio acordado, descuenta el stock y no se entrega dos veces")
    void entregar() throws Exception {
        Long id = crearEncargo();
        cargarStock(taladro, datos.tienda1, 5); // llego la mercaderia
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());

        Long ventaId = leerId(postCon(vendedor, "/api/encargos/{id}/entregar", entregaContado(230), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"))
                .andExpect(jsonPath("$.ventaNumero").value(org.hamcrest.Matchers.matchesPattern("NV\\d{2}-000001")))
                .andReturn(), "$.ventaId");
        getCon(vendedor, "/api/ventas/{id}", ventaId)
                .andExpect(jsonPath("$.total").value(230.0))
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(115.0));
        assertThat(datos.stock(taladro, datos.tienda1)).isEqualByComparingTo("3");

        postCon(vendedor, "/api/encargos/{id}/entregar", entregaContado(230), id)
                .andExpect(status().isUnprocessableContent());
        postCon(vendedor, "/api/encargos/{id}/anular", Map.of("motivo", "Ya no lo quiere"), id)
                .andExpect(status().isUnprocessableContent());
        assertThat(datos.stock(taladro, datos.tienda1)).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("Entregar a credito usa el cliente del encargo")
    void entregarACredito() throws Exception {
        Long clienteId = leerId(postCon(vendedor, "/api/clientes", Map.of("nombre", "Maestro Pedro")).andReturn(), "$.id");
        Map<String, Object> conCliente = encargo(1, 120);
        conCliente.put("clienteId", clienteId);
        Long id = leerId(postCon(vendedor, "/api/encargos", conCliente).andReturn(), "$.id");
        cargarStock(taladro, datos.tienda1, 1);
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());

        postCon(vendedor, "/api/encargos/{id}/entregar", Map.of("condicion", "CREDITO"), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteNombre").value("Maestro Pedro"));
        getCon(vendedor, "/api/fiado/deudas?clienteId={c}", clienteId)
                .andExpect(jsonPath("$.contenido[0].saldoPendiente").value(120.0));
    }

    @Test
    @DisplayName("Anular un encargo pendiente no toca el stock")
    void anular() throws Exception {
        Long id = crearEncargo();
        long movimientos = movimientoRepository.count();
        postCon(vendedor, "/api/encargos/{id}/anular", Map.of("motivo", "El cliente desistio"), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADO"))
                .andExpect(jsonPath("$.observacion").value(org.hamcrest.Matchers.containsString("ANULADO: El cliente desistio")));
        assertThat(movimientoRepository.count()).isEqualTo(movimientos);
        getCon(vendedor, "/api/encargos?estado=ANULADO").andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @DisplayName("Un vendedor de otra tienda no ve ni entrega el encargo; el almacenero no tiene acceso")
    void otraTienda() throws Exception {
        Long id = crearEncargo();
        String otro = datos.token(datos.vendedor2);
        getCon(otro, "/api/encargos/{id}", id).andExpect(status().isForbidden());
        postCon(otro, "/api/encargos/{id}/entregar", entregaContado(230), id).andExpect(status().isForbidden());
        getCon(otro, "/api/encargos").andExpect(jsonPath("$.totalElementos").value(0));
        getCon(datos.token(datos.almacenero), "/api/encargos").andExpect(status().isForbidden());
        assertThat(encargoRepository.findById(id).orElseThrow().getEstado().name()).isEqualTo("PENDIENTE");
    }
}
