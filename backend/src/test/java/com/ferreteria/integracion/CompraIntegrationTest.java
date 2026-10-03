package com.ferreteria.integracion;

import com.ferreteria.entity.MovimientoInventario;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Proveedor;
import com.ferreteria.entity.enums.TipoMovimiento;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.repository.MovimientoInventarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo critico: la compra entra al almacen, suma stock y deja kardex; la anulacion lo revierte.
 */
class CompraIntegrationTest extends IntegracionTestBase {

    @Autowired private MovimientoInventarioRepository movimientoRepository;

    private Producto cemento;
    private Producto clavo;
    private Proveedor proveedor;

    @BeforeEach
    void crearCatalogo() {
        cemento = datos.producto("CEM-01", UnidadBase.UNIDAD, "32.00");
        clavo = datos.producto("CLA-KG", UnidadBase.KILO, "6.50", "Caja 25 kg", "25", "150.00");
        proveedor = datos.proveedor("20555555551");
    }

    private Map<String, Object> compra(String serieNumero) {
        Map<String, Object> compra = new HashMap<>();
        compra.put("empresaId", datos.empresa1.getId());
        compra.put("proveedorId", proveedor.getId());
        compra.put("tipoComprobante", "FACTURA");
        compra.put("serieNumero", serieNumero);
        compra.put("fechaEmision", hoyLima().toString());
        compra.put("detalles", List.of(
                Map.of("productoId", cemento.getId(), "cantidad", 10, "precioUnitario", 30.00),
                Map.of("productoId", clavo.getId(), "presentacionId", datos.presentacionId(clavo, 1),
                        "cantidad", 2, "precioUnitario", 100.00)));
        return compra;
    }

    @Test
    @DisplayName("El almacenero registra una compra: entra al almacen, suma stock y deja kardex con costo")
    void compraEntraAlAlmacen() throws Exception {
        postCon(datos.token(datos.almacenero), "/api/compras", compra("F001-123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ubicacionNombre").value("Almacen Central"))
                .andExpect(jsonPath("$.total").value(500.00))
                .andExpect(jsonPath("$.subtotal").value(423.73))
                .andExpect(jsonPath("$.igv").value(76.27))
                .andExpect(jsonPath("$.detalles[1].cantidadBase").value(50))
                .andExpect(jsonPath("$.detalles[1].costoUnitario").value(4.0));

        assertThat(datos.stock(cemento, datos.almacen)).isEqualByComparingTo("10");
        assertThat(datos.stock(clavo, datos.almacen)).isEqualByComparingTo("50");
        List<MovimientoInventario> kardex = movimientoRepository.findAll();
        assertThat(kardex).hasSize(2).allMatch(m -> m.getTipo() == TipoMovimiento.COMPRA && m.getCompra() != null);
        assertThat(kardex).extracting(m -> m.getSaldoResultante().intValue()).containsExactlyInAnyOrder(10, 50);
    }

    @Test
    @DisplayName("Una segunda compra suma al stock existente")
    void segundaCompraSuma() throws Exception {
        String token = datos.token(datos.almacenero);
        postCon(token, "/api/compras", compra("F001-1")).andExpect(status().isCreated());
        postCon(token, "/api/compras", compra("F001-2")).andExpect(status().isCreated());

        assertThat(datos.stock(cemento, datos.almacen)).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("El mismo comprobante del mismo proveedor no se registra dos veces")
    void comprobanteDuplicado() throws Exception {
        String token = datos.token(datos.almacenero);
        postCon(token, "/api/compras", compra("F001-9")).andExpect(status().isCreated());
        postCon(token, "/api/compras", compra("f001-9"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
        assertThat(datos.stock(cemento, datos.almacen)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("El almacenero no puede registrar compras en una tienda; el ADMIN debe indicar la ubicacion")
    void ubicacionDeLaCompra() throws Exception {
        Map<String, Object> compra = compra(null);
        compra.put("ubicacionId", datos.tienda1.getId());
        postCon(datos.token(datos.almacenero), "/api/compras", compra).andExpect(status().isForbidden());

        compra.remove("ubicacionId");
        postCon(datos.token(datos.admin), "/api/compras", compra).andExpect(status().isUnprocessableContent());

        compra.put("ubicacionId", datos.tienda1.getId());
        compra.put("empresaId", datos.empresa2.getId()); // la tienda 1 es de la empresa 1
        postCon(datos.token(datos.admin), "/api/compras", compra).andExpect(status().isUnprocessableContent());

        compra.put("empresaId", datos.empresa1.getId());
        postCon(datos.token(datos.admin), "/api/compras", compra).andExpect(status().isCreated());
        assertThat(datos.stock(cemento, datos.tienda1)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Anular una compra retira el stock y deja kardex ANULACION_COMPRA; no se anula dos veces")
    void anularCompra() throws Exception {
        MvcResult creada = postCon(datos.token(datos.almacenero), "/api/compras", compra("F001-5")).andReturn();
        Long compraId = leerId(creada, "$.id");

        postCon(datos.token(datos.almacenero), "/api/compras/{id}/anular", Map.of("motivo", "Error de digitacion"),
                compraId).andExpect(status().isForbidden());

        String admin = datos.token(datos.admin);
        postCon(admin, "/api/compras/{id}/anular", Map.of("motivo", "Error de digitacion"), compraId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADA"))
                .andExpect(jsonPath("$.observacion").value("ANULADA: Error de digitacion"));

        assertThat(datos.stock(cemento, datos.almacen)).isEqualByComparingTo("0");
        assertThat(datos.stock(clavo, datos.almacen)).isEqualByComparingTo("0");
        assertThat(movimientoRepository.findAll()).filteredOn(m -> m.getTipo() == TipoMovimiento.ANULACION_COMPRA)
                .hasSize(2).allMatch(m -> "Error de digitacion".equals(m.getMotivo()));

        postCon(admin, "/api/compras/{id}/anular", Map.of("motivo", "Otra vez"), compraId)
                .andExpect(status().isUnprocessableContent());
        // Ya anulada, el comprobante se puede volver a registrar correctamente
        postCon(datos.token(datos.almacenero), "/api/compras", compra("F001-5")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Validaciones: cantidad 0 y sin proveedor responden 400; fecha futura (en Lima) 422")
    void validaciones() throws Exception {
        Map<String, Object> compra = compra("F1");
        compra.put("detalles", List.of(Map.of("productoId", cemento.getId(), "cantidad", 0, "precioUnitario", 1)));
        compra.remove("proveedorId");
        postCon(datos.token(datos.almacenero), "/api/compras", compra)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle.length()").value(2));

        Map<String, Object> futura = compra("F2");
        futura.put("fechaEmision", hoyLima().plusDays(1).toString());
        postCon(datos.token(datos.almacenero), "/api/compras", futura)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value("La fecha de emision no puede ser futura"));
    }

    @Test
    @DisplayName("Un vendedor no tiene acceso a compras ni proveedores")
    void vendedorSinAcceso() throws Exception {
        String token = datos.token(datos.vendedor1);
        getCon(token, "/api/compras").andExpect(status().isForbidden());
        getCon(token, "/api/proveedores").andExpect(status().isForbidden());
    }
}
