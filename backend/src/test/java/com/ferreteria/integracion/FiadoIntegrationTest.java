package com.ferreteria.integracion;

import com.ferreteria.entity.Cliente;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.TipoDocumentoIdentidad;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.repository.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo critico de fiado: venta a credito, abonos parciales con distintos metodos, deudores y cuadre.
 */
class FiadoIntegrationTest extends IntegracionTestBase {

    @Autowired private ClienteRepository clienteRepository;

    private Producto cemento;
    private Cliente carlos;
    private Cliente rosa;
    private String vendedor;

    @BeforeEach
    void preparar() throws Exception {
        cemento = datos.producto("CEM-1", UnidadBase.UNIDAD, "30.00");
        cargarStock(cemento, datos.tienda1, 100);
        cargarStock(cemento, datos.tienda2, 100);
        carlos = cliente("Carlos Rojas", "11111111");
        rosa = cliente("Rosa Diaz", "22222222");
        vendedor = datos.token(datos.vendedor1);
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
    }

    private Cliente cliente(String nombre, String dni) {
        Cliente cliente = new Cliente();
        cliente.setNombre(nombre);
        cliente.setTipoDocumento(TipoDocumentoIdentidad.DNI);
        cliente.setNumeroDocumento(dni);
        return clienteRepository.save(cliente);
    }

    private Long fiar(String token, Cliente cliente, int bolsas, LocalDate vencimiento) throws Exception {
        Map<String, Object> venta = new HashMap<>(Map.of("condicion", "CREDITO", "clienteId", cliente.getId(),
                "detalles", List.of(Map.of("presentacionId", datos.presentacionId(cemento, 0), "cantidad", bolsas))));
        if (vencimiento != null) {
            venta.put("fechaVencimiento", vencimiento.toString());
        }
        return leerId(postCon(token, "/api/ventas", venta).andExpect(status().isCreated()).andReturn(), "$.id");
    }

    @Test
    @DisplayName("Abonos parciales (efectivo y mixto) hasta cancelar la deuda; no se puede abonar de mas")
    void abonosHastaCancelar() throws Exception {
        Long ventaId = fiar(vendedor, carlos, 3, null); // 90.00

        postCon(vendedor, "/api/fiado/ventas/{id}/abonos", Map.of("pagos",
                List.of(Map.of("metodoPago", "EFECTIVO", "monto", 40))), ventaId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoPendiente").value(50.00))
                .andExpect(jsonPath("$.pagos[0].tipo").value("ABONO"));

        postCon(vendedor, "/api/fiado/ventas/{id}/abonos", Map.of("pagos",
                List.of(Map.of("metodoPago", "EFECTIVO", "monto", 60))), ventaId)
                .andExpect(status().isUnprocessableContent());

        postCon(vendedor, "/api/fiado/ventas/{id}/abonos", Map.of("pagos", List.of(
                        Map.of("metodoPago", "EFECTIVO", "monto", 20),
                        Map.of("metodoPago", "YAPE", "monto", 30, "numeroOperacion", "778899"))), ventaId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoPendiente").value(0))
                .andExpect(jsonPath("$.pagos.length()").value(3));

        postCon(vendedor, "/api/fiado/ventas/{id}/abonos", Map.of("pagos",
                List.of(Map.of("metodoPago", "EFECTIVO", "monto", 1))), ventaId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("ya esta pagada")));

        // El cuadre de la caja incluye los abonos en efectivo (40 + 20)
        Long cajaId = leerId(getCon(vendedor, "/api/cajas/actual")
                .andExpect(jsonPath("$.resumen.totalAbonos").value(90.00))
                .andExpect(jsonPath("$.resumen.efectivoEsperado").value(60.00))
                .andReturn(), "$.id");
        postCon(vendedor, "/api/cajas/{id}/cerrar", Map.of("efectivoContado", 60), cajaId)
                .andExpect(jsonPath("$.diferencia").value(0));
    }

    @Test
    @DisplayName("El abono se cobra en la tienda de la venta y solo a ventas al credito")
    void reglasDeAbono() throws Exception {
        Long ventaId = fiar(vendedor, carlos, 1, null);

        // Vendedor de otra tienda: no puede ni ver la venta
        String otro = datos.token(datos.vendedor2);
        postCon(otro, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        postCon(otro, "/api/fiado/ventas/{id}/abonos", Map.of("pagos",
                List.of(Map.of("metodoPago", "EFECTIVO", "monto", 5))), ventaId)
                .andExpect(status().isForbidden());

        // ADMIN con caja en la tienda 2 no puede cobrar una deuda de la tienda 1
        String admin = datos.token(datos.admin);
        postCon(admin, "/api/cajas/abrir", Map.of("ubicacionId", datos.tienda2.getId(), "montoApertura", 0))
                .andExpect(status().isCreated());
        postCon(admin, "/api/fiado/ventas/{id}/abonos", Map.of("pagos",
                List.of(Map.of("metodoPago", "EFECTIVO", "monto", 5))), ventaId)
                .andExpect(status().isUnprocessableContent());

        Long contadoId = leerId(postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO",
                "detalles", List.of(Map.of("presentacionId", datos.presentacionId(cemento, 0), "cantidad", 1)),
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 30)))).andReturn(), "$.id");
        postCon(vendedor, "/api/fiado/ventas/{id}/abonos", Map.of("pagos",
                List.of(Map.of("metodoPago", "EFECTIVO", "monto", 5))), contadoId)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Reporte de deudores: ordenado por deuda y restringido a la tienda del vendedor")
    void reporteDeudores() throws Exception {
        fiar(vendedor, carlos, 1, null);                         // 30
        fiar(vendedor, rosa, 2, LocalDate.now().plusDays(7));    // 60
        fiar(vendedor, carlos, 1, null);                         // 30 -> Carlos debe 60 en 2 ventas
        fiar(vendedor, rosa, 1, null);                           // 30 -> Rosa debe 90

        String otro = datos.token(datos.vendedor2);
        postCon(otro, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        fiar(otro, carlos, 5, null);                             // 150 en la tienda 2

        getCon(vendedor, "/api/fiado/deudores")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].clienteNombre").value("Rosa Diaz"))
                .andExpect(jsonPath("$.contenido[0].deudaTotal").value(90.00))
                .andExpect(jsonPath("$.contenido[0].proximoVencimiento").value(LocalDate.now().plusDays(7).toString()))
                .andExpect(jsonPath("$.contenido[1].cantidadVentas").value(2));

        getCon(datos.token(datos.admin), "/api/fiado/deudores")
                .andExpect(jsonPath("$.contenido[0].clienteNombre").value("Carlos Rojas"))
                .andExpect(jsonPath("$.contenido[0].deudaTotal").value(210.00));
        getCon(vendedor, "/api/fiado/deudores?ubicacionId={id}", datos.tienda2.getId())
                .andExpect(status().isForbidden());
        getCon(vendedor, "/api/fiado/deudas?clienteId={id}", carlos.getId())
                .andExpect(jsonPath("$.totalElementos").value(2));
        getCon(vendedor, "/api/fiado/deudas?soloVencidas=true").andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    @DisplayName("Anular un fiado con abonos en caja abierta anula tambien los abonos")
    void anularFiadoConAbonos() throws Exception {
        Long ventaId = fiar(vendedor, carlos, 2, null);
        postCon(vendedor, "/api/fiado/ventas/{id}/abonos", Map.of("pagos",
                List.of(Map.of("metodoPago", "EFECTIVO", "monto", 25))), ventaId).andExpect(status().isCreated());

        postCon(vendedor, "/api/ventas/{id}/anular", Map.of("motivo", "Devolvio la mercaderia"), ventaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoPendiente").value(0))
                .andExpect(jsonPath("$.pagos[0].estado").value("ANULADO"));
        getCon(vendedor, "/api/cajas/actual").andExpect(jsonPath("$.resumen.efectivoEsperado").value(0.0));
        getCon(vendedor, "/api/fiado/deudores").andExpect(jsonPath("$.totalElementos").value(0));
    }
}
