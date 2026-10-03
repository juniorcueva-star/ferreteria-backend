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

class ReporteIntegrationTest extends IntegracionTestBase {

    private static final String HOY = hoyLima().toString();
    private static final String RANGO = "desde=" + hoyLima().minusDays(1) + "&hasta=" + hoyLima().plusDays(1);

    private Producto martillo;
    private Producto cable;

    @BeforeEach
    void preparar() throws Exception {
        martillo = datos.producto("MAR-1", UnidadBase.UNIDAD, "25.00");
        cable = datos.producto("CAB-1", UnidadBase.METRO, "2.00");
        for (var tienda : List.of(datos.tienda1, datos.tienda2)) {
            cargarStock(martillo, tienda, 50);
            cargarStock(cable, tienda, 500);
        }
    }

    private Long vender(String token, String condicion, Producto producto, int cantidad, Integer pago) throws Exception {
        Map<String, Object> venta = new java.util.HashMap<>(Map.of("condicion", condicion,
                "detalles", List.of(Map.of("presentacionId", datos.presentacionId(producto, 0), "cantidad", cantidad))));
        if (pago != null) {
            venta.put("pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", pago)));
        }
        if (condicion.equals("CREDITO")) {
            venta.put("clienteId", leerId(postCon(token, "/api/clientes",
                    Map.of("nombre", "Cliente " + System.nanoTime())).andReturn(), "$.id"));
        }
        return leerId(postCon(token, "/api/ventas", venta).andExpect(status().isCreated()).andReturn(), "$.id");
    }

    @Test
    @DisplayName("Ventas por tienda y productos mas vendidos, con alcance por tienda para el vendedor")
    void reportesDeVentas() throws Exception {
        String v1 = datos.token(datos.vendedor1);
        String v2 = datos.token(datos.vendedor2);
        postCon(v1, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        postCon(v2, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());

        vender(v1, "CONTADO", martillo, 2, 50);          // 50
        vender(v1, "CONTADO", cable, 30, 60);            // 60
        vender(v1, "CREDITO", martillo, 1, 5);           // 25, saldo 20
        Long anulada = vender(v1, "CONTADO", cable, 10, 20);
        postCon(v1, "/api/ventas/{id}/anular", Map.of("motivo", "Prueba de reporte"), anulada)
                .andExpect(status().isOk());
        vender(v2, "CONTADO", martillo, 1, 25);          // tienda 2: 25

        getCon(datos.token(datos.admin), "/api/reportes/ventas-por-tienda?" + RANGO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].ubicacionNombre").value("Tienda 1"))
                .andExpect(jsonPath("$.contenido[0].cantidadVentas").value(3))
                .andExpect(jsonPath("$.contenido[0].totalVendido").value(135.00))
                .andExpect(jsonPath("$.contenido[0].totalContado").value(110.00))
                .andExpect(jsonPath("$.contenido[0].totalCredito").value(25.00))
                .andExpect(jsonPath("$.contenido[0].saldoPendiente").value(20.00))
                .andExpect(jsonPath("$.contenido[0].cantidadAnuladas").value(1))
                .andExpect(jsonPath("$.contenido[1].totalVendido").value(25.00));

        getCon(v1, "/api/reportes/ventas-por-tienda?" + RANGO)
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].ubicacionNombre").value("Tienda 1"));
        getCon(v1, "/api/reportes/ventas-por-tienda?" + RANGO + "&ubicacionId=" + datos.tienda2.getId())
                .andExpect(status().isForbidden());

        getCon(datos.token(datos.admin), "/api/reportes/productos-mas-vendidos?" + RANGO)
                .andExpect(jsonPath("$.contenido[0].productoCodigo").value("MAR-1"))
                .andExpect(jsonPath("$.contenido[0].montoVendido").value(100.00))
                .andExpect(jsonPath("$.contenido[0].numeroVentas").value(3));
        getCon(datos.token(datos.admin), "/api/reportes/productos-mas-vendidos?orden=CANTIDAD&" + RANGO)
                .andExpect(jsonPath("$.contenido[0].productoCodigo").value("CAB-1"))
                .andExpect(jsonPath("$.contenido[0].cantidadVendida").value(30));
        getCon(v2, "/api/reportes/productos-mas-vendidos?" + RANGO)
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].montoVendido").value(25.00));
    }

    @Test
    @DisplayName("Traslados por tienda y compras por proveedor")
    void reportesDeAlmacen() throws Exception {
        String almacenero = datos.token(datos.almacenero);
        cargarStock(martillo, datos.almacen, 100);
        Long t1 = leerId(postCon(almacenero, "/api/traslados", Map.of("destinoId", datos.tienda1.getId(),
                "detalles", List.of(Map.of("productoId", martillo.getId(), "cantidad", 5)))).andReturn(), "$.id");
        postCon(almacenero, "/api/traslados", Map.of("destinoId", datos.tienda1.getId(),
                "detalles", List.of(Map.of("productoId", martillo.getId(), "cantidad", 5)))).andExpect(status().isCreated());
        Long t3 = leerId(postCon(almacenero, "/api/traslados", Map.of("destinoId", datos.tienda2.getId(),
                "detalles", List.of(Map.of("productoId", martillo.getId(), "cantidad", 5)))).andReturn(), "$.id");
        postCon(datos.token(datos.vendedor1), "/api/traslados/{id}/recibir", Map.of(), t1).andExpect(status().isOk());
        postCon(almacenero, "/api/traslados/{id}/anular", Map.of("motivo", "Pedido cancelado"), t3)
                .andExpect(status().isOk());

        getCon(almacenero, "/api/reportes/traslados-por-tienda?" + RANGO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].destinoNombre").value("Tienda 1"))
                .andExpect(jsonPath("$.contenido[0].cantidadTraslados").value(2))
                .andExpect(jsonPath("$.contenido[0].recibidos").value(1))
                .andExpect(jsonPath("$.contenido[0].enCamino").value(1))
                .andExpect(jsonPath("$.contenido[1].anulados").value(1));

        Long p1 = datos.proveedor("20100000001").getId();
        Long p2 = datos.proveedor("20100000002").getId();
        for (Object[] compra : new Object[][]{{p1, 100}, {p1, 50}, {p2, 400}}) {
            postCon(almacenero, "/api/compras", Map.of("empresaId", datos.empresa1.getId(), "proveedorId", compra[0],
                    "tipoComprobante", "FACTURA", "fechaEmision", HOY, "detalles", List.of(
                            Map.of("productoId", martillo.getId(), "cantidad", 1, "precioUnitario", compra[1]))))
                    .andExpect(status().isCreated());
        }
        getCon(datos.token(datos.admin), "/api/reportes/compras-por-proveedor?" + RANGO)
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].proveedorId").value(p2))
                .andExpect(jsonPath("$.contenido[1].cantidadCompras").value(2))
                .andExpect(jsonPath("$.contenido[1].totalComprado").value(150.00))
                .andExpect(jsonPath("$.contenido[1].ultimaCompra").value(HOY));
    }

    @Test
    @DisplayName("Permisos y validaciones de los reportes")
    void permisos() throws Exception {
        String v1 = datos.token(datos.vendedor1);
        getCon(v1, "/api/reportes/compras-por-proveedor?" + RANGO).andExpect(status().isForbidden());
        getCon(v1, "/api/reportes/traslados-por-tienda?" + RANGO).andExpect(status().isForbidden());
        getCon(datos.token(datos.almacenero), "/api/reportes/ventas-por-tienda?" + RANGO)
                .andExpect(status().isForbidden());
        getCon(v1, "/api/reportes/ventas-por-tienda?hasta=" + HOY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle[0]").value("desde: es obligatorio"));
        getCon(v1, "/api/reportes/ventas-por-tienda?desde=" + HOY + "&hasta=" + hoyLima().minusDays(3))
                .andExpect(status().isUnprocessableContent());
        getCon(v1, "/api/reportes/ventas-por-tienda?desde=ayer&hasta=" + HOY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));

        putCon(datos.token(datos.admin), "/api/inventario/stock/minimo", Map.of("productoId", martillo.getId(),
                "ubicacionId", datos.tienda1.getId(), "stockMinimo", 60)).andExpect(status().isOk());
        getCon(v1, "/api/reportes/stock-bajo")
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].productoCodigo").value("MAR-1"));
        getCon(datos.token(datos.vendedor2), "/api/reportes/stock-bajo").andExpect(jsonPath("$.totalElementos").value(0));
    }
}
