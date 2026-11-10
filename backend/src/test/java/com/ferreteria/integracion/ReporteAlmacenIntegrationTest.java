package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Proveedor;
import com.ferreteria.entity.enums.UnidadBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reportes del almacen: pedido sugerido al proveedor, valor del inventario y ubicacion fisica. */
class ReporteAlmacenIntegrationTest extends IntegracionTestBase {

    private Producto perno;
    private Producto martillo;
    private Proveedor proveedor;
    private String almacenero;

    @BeforeEach
    void preparar() throws Exception {
        perno = datos.producto("PER-1", UnidadBase.UNIDAD, "0.50");
        martillo = datos.producto("MAR-1", UnidadBase.UNIDAD, "30.00");
        proveedor = datos.proveedor("20555555551");
        almacenero = datos.token(datos.almacenero);
        Map<String, Object> compra = new HashMap<>();
        compra.put("empresaId", datos.empresa1.getId());
        compra.put("proveedorId", proveedor.getId());
        compra.put("tipoComprobante", "FACTURA");
        compra.put("serieNumero", "F001-1");
        compra.put("fechaEmision", hoyLima().toString());
        compra.put("detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 20, "precioUnitario", 0.354)));
        postCon(almacenero, "/api/compras", compra).andExpect(status().isCreated());
        cargarStock(martillo, datos.almacen, 4); // sin compras: no tiene costo
        putCon(datos.token(datos.admin), "/api/inventario/stock/minimo", Map.of("productoId", perno.getId(),
                "ubicacionId", datos.almacen.getId(), "stockMinimo", 50)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Pedido sugerido: hasta el doble del minimo con el ultimo proveedor y costo")
    void pedidoSugerido() throws Exception {
        String json = getCon(almacenero, "/api/reportes/pedido-sugerido")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productoCodigo").value("PER-1"))
                .andExpect(jsonPath("$[0].cantidadSugerida").value(80))   // 2 x 50 - 20
                .andExpect(jsonPath("$[0].ultimoProveedor").value(proveedor.getRazonSocial()))
                .andReturn().getResponse().getContentAsString();
        BigDecimal costo = new BigDecimal(JsonPath.read(json, "$[0].ultimoCosto").toString());
        BigDecimal monto = new BigDecimal(JsonPath.read(json, "$[0].montoEstimado").toString());
        assertThat(costo.signum()).isPositive();
        assertThat(monto).isEqualByComparingTo(costo.multiply(BigDecimal.valueOf(80)).setScale(2, RoundingMode.HALF_UP));

        getCon(datos.token(datos.vendedor1), "/api/reportes/pedido-sugerido").andExpect(status().isForbidden());
        getCon(almacenero, "/api/reportes/excel/pedido-sugerido").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Valor del inventario: cantidad x costo, cuenta los que no tienen costo y filtra los que no salen")
    void valorInventario() throws Exception {
        String json = getCon(almacenero, "/api/reportes/valor-inventario")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos").value(2))
                .andExpect(jsonPath("$.productosSinCosto").value(1))
                .andReturn().getResponse().getContentAsString();
        BigDecimal costo = new BigDecimal(JsonPath.read(json, "$.filas[?(@.productoCodigo == 'PER-1')].costoUnitario")
                .toString().replaceAll("[\\[\\]]", ""));
        assertThat(new BigDecimal(JsonPath.read(json, "$.valorTotal").toString()))
                .isEqualByComparingTo(costo.multiply(BigDecimal.valueOf(20)).setScale(2, RoundingMode.HALF_UP));

        // El perno sale hoy en un traslado: ya no aparece entre los que no salen hace 30 dias
        postCon(almacenero, "/api/traslados", Map.of("destinoId", datos.tienda1.getId(),
                "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 5)))).andExpect(status().isCreated());
        getCon(almacenero, "/api/reportes/valor-inventario?diasSinSalida=30")
                .andExpect(jsonPath("$.filas.length()").value(1))
                .andExpect(jsonPath("$.filas[0].productoCodigo").value("MAR-1"))
                .andExpect(jsonPath("$.filas[0].ultimaSalida").value(nullValue()));
        getCon(almacenero, "/api/reportes/excel/valor-inventario?diasSinSalida=30").andExpect(status().isOk());
    }

    @Test
    @DisplayName("El almacenero guarda donde esta cada producto; el vendedor no puede")
    void ubicacionFisica() throws Exception {
        putCon(almacenero, "/api/inventario/stock/ubicacion-fisica", Map.of("productoId", perno.getId(),
                "ubicacionFisica", "  Pasillo 3 - Estante B "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ubicacionFisica").value("Pasillo 3 - Estante B"));
        getCon(almacenero, "/api/inventario/stock?productoId={id}", perno.getId())
                .andExpect(jsonPath("$.contenido[0].ubicacionFisica").value("Pasillo 3 - Estante B"));
        putCon(datos.token(datos.vendedor1), "/api/inventario/stock/ubicacion-fisica", Map.of("productoId", perno.getId(),
                "ubicacionFisica", "Mostrador")).andExpect(status().isForbidden());
    }
}
