package com.ferreteria.integracion;

import com.ferreteria.entity.Cliente;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.TipoDocumentoIdentidad;
import com.ferreteria.entity.enums.TipoMovimiento;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.repository.ClienteRepository;
import com.ferreteria.repository.MovimientoInventarioRepository;
import com.ferreteria.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujos criticos de venta: contado con vuelto, credito con pago mixto, anulacion que devuelve stock
 * y cuadre de caja con todo lo anterior.
 */
class VentaIntegrationTest extends IntegracionTestBase {

    @Autowired private ClienteRepository clienteRepository;
    @Autowired private MovimientoInventarioRepository movimientoRepository;
    @Autowired private VentaRepository ventaRepository;

    private Producto clavo;
    private Producto alambre;
    private Cliente cliente;
    private String vendedor;

    @BeforeEach
    void prepararTienda() throws Exception {
        clavo = datos.producto("CLA-2", UnidadBase.UNIDAD, "0.10", "Ciento", "100", "8.00");
        alambre = datos.producto("ALA-16", UnidadBase.KILO, "5.00");
        cargarStock(clavo, datos.tienda1, 1000);
        cargarStock(alambre, datos.tienda1, 20);
        Cliente nuevo = new Cliente();
        nuevo.setNombre("Maestro Carlos");
        nuevo.setTipoDocumento(TipoDocumentoIdentidad.DNI);
        nuevo.setNumeroDocumento("40404040");
        cliente = clienteRepository.save(nuevo);
        vendedor = datos.token(datos.vendedor1);
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 100)).andExpect(status().isCreated());
    }

    /** 2 cientos de clavo (16.00) + 1.5 kg de alambre (7.50) = 23.50 */
    private List<Map<String, Object>> detalles() {
        return List.of(
                Map.of("presentacionId", datos.presentacionId(clavo, 1), "cantidad", 2),
                Map.of("presentacionId", datos.presentacionId(alambre, 0), "cantidad", 1.5));
    }

    private MvcResult venderContado() throws Exception {
        return postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO", "detalles", detalles(),
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 30)))).andReturn();
    }

    @Test
    @DisplayName("Venta al contado: descuenta stock, deja kardex, numera y calcula IGV y vuelto")
    void ventaContado() throws Exception {
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO", "detalles", detalles(),
                        "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroDocumento").value(matchesPattern("NV\\d{2}-000001")))
                .andExpect(jsonPath("$.tipoDocumento").value("NOTA_VENTA"))
                .andExpect(jsonPath("$.empresaRuc").value("20111111111"))
                .andExpect(jsonPath("$.total").value(23.50))
                .andExpect(jsonPath("$.subtotal").value(19.92))
                .andExpect(jsonPath("$.igv").value(3.58))
                .andExpect(jsonPath("$.saldoPendiente").value(0))
                .andExpect(jsonPath("$.vuelto").value(6.50))
                .andExpect(jsonPath("$.pagos[0].monto").value(23.50))
                .andExpect(jsonPath("$.detalles[0].cantidadBase").value(200));

        assertThat(datos.stock(clavo, datos.tienda1)).isEqualByComparingTo("800");
        assertThat(datos.stock(alambre, datos.tienda1)).isEqualByComparingTo("18.5");
        assertThat(movimientoRepository.findAll()).filteredOn(m -> m.getTipo() == TipoMovimiento.VENTA)
                .hasSize(2).allMatch(m -> m.getVenta() != null && m.getCantidad().signum() < 0);

        postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO", "detalles", detalles(),
                        "pagos", List.of(Map.of("metodoPago", "YAPE", "monto", 23.50, "numeroOperacion", "123456"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroDocumento").value(matchesPattern("NV\\d{2}-000002")));
    }

    @Test
    @DisplayName("Venta a credito con pago mixto: adelanto en efectivo y Yape, el resto queda como deuda")
    void ventaCreditoPagoMixto() throws Exception {
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CREDITO", "clienteId", cliente.getId(),
                        "detalles", detalles(), "pagos", List.of(
                                Map.of("metodoPago", "EFECTIVO", "monto", 10),
                                Map.of("metodoPago", "YAPE", "monto", 5, "numeroOperacion", "987654"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.condicion").value("CREDITO"))
                .andExpect(jsonPath("$.clienteNombre").value("Maestro Carlos"))
                .andExpect(jsonPath("$.total").value(23.50))
                .andExpect(jsonPath("$.saldoPendiente").value(8.50))
                .andExpect(jsonPath("$.pagos.length()").value(2));

        // Fiado sin adelanto tambien es valido
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CREDITO", "clienteId", cliente.getId(),
                        "detalles", detalles()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoPendiente").value(23.50));
        assertThat(datos.stock(clavo, datos.tienda1)).isEqualByComparingTo("600");
    }

    @Test
    @DisplayName("Reglas de pago: credito sin cliente, Yape sin operacion, pago insuficiente o vuelto en Yape")
    void reglasDePago() throws Exception {
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CREDITO", "detalles", detalles()))
                .andExpect(status().isUnprocessableContent());
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO", "detalles", detalles(),
                        "pagos", List.of(Map.of("metodoPago", "YAPE", "monto", 23.50))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("numero de operacion")));
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO", "detalles", detalles(),
                        "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 20))))
                .andExpect(status().isUnprocessableContent());
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO", "detalles", detalles(),
                        "pagos", List.of(Map.of("metodoPago", "TARJETA", "monto", 50, "numeroOperacion", "1"))))
                .andExpect(status().isUnprocessableContent());
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CREDITO", "clienteId", cliente.getId(),
                        "detalles", detalles(), "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 99))))
                .andExpect(status().isUnprocessableContent());
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO", "detalles", detalles(),
                        "pagos", List.of(Map.of("metodoPago", "BITCOIN", "monto", 30))))
                .andExpect(status().isUnprocessableContent());
        assertThat(ventaRepository.count()).isZero();
        assertThat(datos.stock(clavo, datos.tienda1)).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Sin stock suficiente la venta se rechaza completa: no queda venta, pago ni numero usado")
    void ventaSinStock() throws Exception {
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO",
                        "detalles", List.of(Map.of("presentacionId", datos.presentacionId(alambre, 0), "cantidad", 25)),
                        "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 200))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"));
        assertThat(ventaRepository.count()).isZero();
        assertThat(datos.stock(alambre, datos.tienda1)).isEqualByComparingTo("20");

        MvcResult siguiente = venderContado();
        assertThat((String) leer(siguiente, "$.numeroDocumento")).endsWith("-000001");
    }

    @Test
    @DisplayName("Sin caja abierta no se puede vender; un almacenero no vende")
    void sinCaja() throws Exception {
        postCon(datos.token(datos.vendedor2), "/api/ventas", Map.of("condicion", "CONTADO", "detalles", detalles(),
                        "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 30))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value("Debe abrir su caja antes de cobrar"));
        postCon(datos.token(datos.almacenero), "/api/ventas", Map.of("condicion", "CONTADO", "detalles", detalles()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Anular una venta devuelve el stock, anula sus pagos y no se puede repetir")
    void anularVenta() throws Exception {
        Long ventaId = leerId(venderContado(), "$.id");

        postCon(datos.token(datos.vendedor2), "/api/ventas/{id}/anular", Map.of("motivo", "Cliente se arrepintio"),
                ventaId).andExpect(status().isForbidden());

        postCon(vendedor, "/api/ventas/{id}/anular", Map.of("motivo", "Cliente se arrepintio"), ventaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADA"))
                .andExpect(jsonPath("$.motivoAnulacion").value("Cliente se arrepintio"))
                .andExpect(jsonPath("$.pagos[0].estado").value("ANULADO"));

        assertThat(datos.stock(clavo, datos.tienda1)).isEqualByComparingTo("1000");
        assertThat(datos.stock(alambre, datos.tienda1)).isEqualByComparingTo("20");
        assertThat(movimientoRepository.findAll()).filteredOn(m -> m.getTipo() == TipoMovimiento.ANULACION_VENTA)
                .hasSize(2);
        getCon(vendedor, "/api/cajas/actual")
                .andExpect(jsonPath("$.resumen.cantidadVentas").value(0))
                .andExpect(jsonPath("$.resumen.efectivoEsperado").value(100.0));

        postCon(vendedor, "/api/ventas/{id}/anular", Map.of("motivo", "Otra vez"), ventaId)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Cuadre de caja: esperado = apertura + efectivo cobrado; los pagos digitales no cuentan")
    void cuadreDeCaja() throws Exception {
        venderContado(); // 23.50 en efectivo
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CREDITO", "clienteId", cliente.getId(),
                "detalles", detalles(), "pagos", List.of(
                        Map.of("metodoPago", "EFECTIVO", "monto", 10),
                        Map.of("metodoPago", "PLIN", "monto", 5, "numeroOperacion", "555")))).andReturn();
        Long cajaId = leerId(getCon(vendedor, "/api/cajas/actual")
                .andExpect(jsonPath("$.resumen.cantidadVentas").value(2))
                .andExpect(jsonPath("$.resumen.totalVendido").value(47.00))
                .andExpect(jsonPath("$.resumen.totalCredito").value(8.50))
                .andExpect(jsonPath("$.resumen.totalCobrado").value(38.50))
                .andReturn(), "$.id");

        postCon(vendedor, "/api/cajas/{id}/cerrar", Map.of("efectivoContado", 133.00), cajaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.efectivoEsperado").value(133.50))
                .andExpect(jsonPath("$.diferencia").value(-0.50))
                .andExpect(jsonPath("$.resumen.cobrosPorMetodo[0].codigo").value("EFECTIVO"))
                .andExpect(jsonPath("$.resumen.cobrosPorMetodo[0].monto").value(33.50))
                .andExpect(jsonPath("$.resumen.cobrosPorMetodo[1].codigo").value("PLIN"));

        // Con la caja cerrada ya no se vende
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO", "detalles", detalles(),
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 30))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Con la caja cerrada: el vendedor no anula; el ADMIN tampoco si hubo cobros en esa caja")
    void anularConCajaCerrada() throws Exception {
        Long contado = leerId(venderContado(), "$.id");
        Long fiadoSinAdelanto = leerId(postCon(vendedor, "/api/ventas", Map.of("condicion", "CREDITO",
                "clienteId", cliente.getId(), "detalles", detalles())).andReturn(), "$.id");
        Long cajaId = leerId(getCon(vendedor, "/api/cajas/actual").andReturn(), "$.id");
        postCon(vendedor, "/api/cajas/{id}/cerrar", Map.of("efectivoContado", 123.50), cajaId)
                .andExpect(status().isOk());

        postCon(vendedor, "/api/ventas/{id}/anular", Map.of("motivo", "Error"), fiadoSinAdelanto)
                .andExpect(status().isUnprocessableContent());
        String admin = datos.token(datos.admin);
        postCon(admin, "/api/ventas/{id}/anular", Map.of("motivo", "Error de cobro"), contado)
                .andExpect(status().isUnprocessableContent());
        postCon(admin, "/api/ventas/{id}/anular", Map.of("motivo", "Error de registro"), fiadoSinAdelanto)
                .andExpect(status().isOk());
        assertThat(datos.stock(clavo, datos.tienda1)).isEqualByComparingTo("800");
    }

    @Test
    @DisplayName("Un vendedor solo ve las ventas de su tienda")
    void ventasPorTienda() throws Exception {
        Long ventaId = leerId(venderContado(), "$.id");
        String otro = datos.token(datos.vendedor2);
        getCon(otro, "/api/ventas/{id}", ventaId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        getCon(otro, "/api/ventas").andExpect(jsonPath("$.totalElementos").value(0));
        getCon(otro, "/api/ventas?ubicacionId={id}", datos.tienda1.getId()).andExpect(status().isForbidden());
        getCon(vendedor, "/api/ventas").andExpect(jsonPath("$.totalElementos").value(1));
        getCon(datos.token(datos.admin), "/api/ventas?ubicacionId={id}", datos.tienda1.getId())
                .andExpect(jsonPath("$.totalElementos").value(1));
    }
}
