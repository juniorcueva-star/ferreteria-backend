package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regla de decimales por presentacion (D8): una presentacion con 0 decimales no acepta fracciones; kilo y metro
 * aceptan hasta 3. Se aplica en ventas y compras.
 */
class DecimalesIntegrationTest extends IntegracionTestBase {

    private Producto perno;
    private Producto alambre;
    private String vendedor;

    @BeforeEach
    void preparar() throws Exception {
        perno = datos.producto("PER-1", UnidadBase.UNIDAD, "0.50", "Docena", "12", "5.50");
        alambre = datos.producto("ALA-1", UnidadBase.KILO, "6.00");
        cargarStock(perno, datos.tienda1, 500);
        cargarStock(alambre, datos.tienda1, 50);
        vendedor = datos.token(datos.vendedor1);
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
    }

    private Map<String, Object> venta(Long presentacionId, Number cantidad) {
        return Map.of("condicion", "CONTADO",
                "detalles", List.of(Map.of("presentacionId", presentacionId, "cantidad", cantidad)),
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 100)));
    }

    @Test
    @DisplayName("Venta: 1.5 docenas se rechaza con 422 y 2 docenas se acepta")
    void ventaDocena() throws Exception {
        postCon(vendedor, "/api/ventas", venta(datos.presentacionId(perno, 1), 1.5))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value("La presentacion Docena de Producto PER-1 no acepta fracciones"));
        postCon(vendedor, "/api/ventas", venta(datos.presentacionId(perno, 1), 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detalles[0].cantidadBase").value(24));
    }

    @Test
    @DisplayName("Venta: el kilo acepta 0.250 pero no 0.2505")
    void ventaKilo() throws Exception {
        postCon(vendedor, "/api/ventas", venta(datos.presentacionId(alambre, 0), 0.25))
                .andExpect(status().isCreated());
        postCon(vendedor, "/api/ventas", venta(datos.presentacionId(alambre, 0), 0.2505))
                .andExpect(status().isBadRequest()); // Bean Validation: maximo 3 decimales en el request
    }

    @Test
    @DisplayName("Venta a credito tambien valida decimales")
    void ventaCredito() throws Exception {
        Long clienteId = leerId(postCon(vendedor, "/api/clientes", Map.of("nombre", "Cliente credito")).andReturn(),
                "$.id");
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CREDITO", "clienteId", clienteId,
                "detalles", List.of(Map.of("presentacionId", datos.presentacionId(perno, 1), "cantidad", 0.5))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Compra: fraccion de docena o de unidad base rechazada; kilo fraccionado aceptado")
    void compra() throws Exception {
        String almacenero = datos.token(datos.almacenero);
        Long proveedorId = datos.proveedor("20777777771").getId();
        Map<String, Object> compra = new HashMap<>(Map.of("empresaId", datos.empresa1.getId(),
                "proveedorId", proveedorId, "tipoComprobante", "BOLETA", "fechaEmision", hoyLima().toString()));

        compra.put("detalles", List.of(Map.of("productoId", perno.getId(), "presentacionId",
                datos.presentacionId(perno, 1), "cantidad", 2.5, "precioUnitario", 4)));
        postCon(almacenero, "/api/compras", compra).andExpect(status().isUnprocessableContent());

        compra.put("detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 10.5, "precioUnitario", 1)));
        postCon(almacenero, "/api/compras", compra)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value("La presentacion UNIDAD de Producto PER-1 no acepta fracciones"));

        compra.put("detalles", List.of(Map.of("productoId", alambre.getId(), "cantidad", 12.75, "precioUnitario", 4)));
        postCon(almacenero, "/api/compras", compra).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Las presentaciones informan sus decimales y se pueden fijar al crearlas")
    void decimalesEnPresentacion() throws Exception {
        String admin = datos.token(datos.admin);
        postCon(admin, "/api/productos", Map.of("codigo", "CAB-1", "nombre", "Cable", "categoriaId", 1,
                "unidadBase", "METRO", "presentaciones", List.of(
                        Map.of("nombre", "Metro", "factor", 1, "precioVenta", 1.5),
                        Map.of("nombre", "Rollo 100 m", "factor", 100, "precioVenta", 120, "decimales", 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.marca").value("VARIADO"))
                .andExpect(jsonPath("$.presentaciones[0].decimales").value(3))
                .andExpect(jsonPath("$.presentaciones[1].decimales").value(0));
    }
}
