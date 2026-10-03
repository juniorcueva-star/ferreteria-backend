package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo critico: traslado del almacen a una tienda en dos pasos (enviar y recibir), ajustes y kardex.
 */
class InventarioIntegrationTest extends IntegracionTestBase {

    private Producto cable;
    private Producto perno;

    @BeforeEach
    void crearStock() throws Exception {
        cable = datos.producto("CAB-14", UnidadBase.METRO, "1.20", "Rollo 100 m", "100", "100.00");
        perno = datos.producto("PER-38", UnidadBase.UNIDAD, "0.50", "Caja x 50", "50", "20.00");
        cargarStock(cable, datos.almacen, 500);
        cargarStock(perno, datos.almacen, 1000);
    }

    private Map<String, Object> traslado() {
        return Map.of("destinoId", datos.tienda1.getId(), "observacion", "Reposicion semanal",
                "detalles", List.of(
                        Map.of("productoId", cable.getId(), "presentacionId", datos.presentacionId(cable, 1), "cantidad", 2),
                        Map.of("productoId", perno.getId(), "cantidad", 150)));
    }

    @Test
    @DisplayName("Traslado: enviar resta del almacen, recibir suma en la tienda y queda el kardex de ambos lados")
    void enviarYRecibir() throws Exception {
        MvcResult enviado = postCon(datos.token(datos.almacenero), "/api/traslados", traslado())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ENVIADO"))
                .andExpect(jsonPath("$.codigo").value(org.hamcrest.Matchers.matchesPattern("TR-\\d{6}")))
                .andExpect(jsonPath("$.origenNombre").value("Almacen Central"))
                .andExpect(jsonPath("$.detalles[0].cantidadBase").value(200))
                .andReturn();
        Long id = leerId(enviado, "$.id");

        // Mientras esta en camino no figura en ninguna ubicacion
        assertThat(datos.stock(cable, datos.almacen)).isEqualByComparingTo("300");
        assertThat(datos.stock(perno, datos.almacen)).isEqualByComparingTo("850");
        assertThat(datos.stock(cable, datos.tienda1)).isEqualByComparingTo("0");

        // Un vendedor de otra tienda no puede recibirlo
        postCon(datos.token(datos.vendedor2), "/api/traslados/{id}/recibir", Map.of(), id)
                .andExpect(status().isForbidden());

        postCon(datos.token(datos.vendedor1), "/api/traslados/{id}/recibir", Map.of(), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECIBIDO"))
                .andExpect(jsonPath("$.usuarioRecibe").value("vendedor1"))
                .andExpect(jsonPath("$.fechaRecepcion").exists());
        assertThat(datos.stock(cable, datos.tienda1)).isEqualByComparingTo("200");
        assertThat(datos.stock(perno, datos.tienda1)).isEqualByComparingTo("150");

        // No se recibe dos veces ni se anula ya recibido
        postCon(datos.token(datos.vendedor1), "/api/traslados/{id}/recibir", Map.of(), id)
                .andExpect(status().isUnprocessableContent());
        postCon(datos.token(datos.almacenero), "/api/traslados/{id}/anular", Map.of("motivo", "Ya no va"), id)
                .andExpect(status().isUnprocessableContent());

        getCon(datos.token(datos.admin), "/api/inventario/kardex?productoId={p}", cable.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.contenido[0].tipo").value("TRASLADO_ENTRADA"))
                .andExpect(jsonPath("$.contenido[0].saldoResultante").value(200))
                .andExpect(jsonPath("$.contenido[1].tipo").value("TRASLADO_SALIDA"))
                .andExpect(jsonPath("$.contenido[1].cantidad").value(-200))
                .andExpect(jsonPath("$.contenido[1].documento").value(org.hamcrest.Matchers.startsWith("TR-")));
    }

    @Test
    @DisplayName("No se puede trasladar mas de lo que hay: 409 STOCK_INSUFICIENTE y nada cambia")
    void trasladoSinStock() throws Exception {
        postCon(datos.token(datos.almacenero), "/api/traslados", Map.of("destinoId", datos.tienda1.getId(),
                "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 1001))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"))
                .andExpect(jsonPath("$.detalle[0]").value("disponible: 1000.000"));
        assertThat(datos.stock(perno, datos.almacen)).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Anular un traslado ENVIADO devuelve el stock al origen")
    void anularTraslado() throws Exception {
        Long id = leerId(postCon(datos.token(datos.almacenero), "/api/traslados", traslado()).andReturn(), "$.id");
        postCon(datos.token(datos.almacenero), "/api/traslados/{id}/anular", Map.of("motivo", "Camion averiado"), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADO"));
        assertThat(datos.stock(cable, datos.almacen)).isEqualByComparingTo("500");
        postCon(datos.token(datos.vendedor1), "/api/traslados/{id}/recibir", Map.of(), id)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Un vendedor no envia traslados ni ve traslados de otra tienda")
    void permisosDeTraslado() throws Exception {
        postCon(datos.token(datos.vendedor1), "/api/traslados", traslado()).andExpect(status().isForbidden());
        Long id = leerId(postCon(datos.token(datos.almacenero), "/api/traslados", traslado()).andReturn(), "$.id");

        getCon(datos.token(datos.vendedor2), "/api/traslados/{id}", id).andExpect(status().isForbidden());
        getCon(datos.token(datos.vendedor2), "/api/traslados")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
        getCon(datos.token(datos.vendedor1), "/api/traslados")
                .andExpect(jsonPath("$.totalElementos").value(1));
        getCon(datos.token(datos.vendedor1), "/api/traslados?ubicacionId={u}", datos.tienda2.getId())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Ajustes: la salida exige motivo y no deja stock negativo; el almacenero no ajusta tiendas")
    void ajustes() throws Exception {
        String almacenero = datos.token(datos.almacenero);
        postCon(almacenero, "/api/inventario/ajustes", Map.of("tipo", "SALIDA", "motivo", "Rotura de caja",
                "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].tipo").value("AJUSTE_SALIDA"))
                .andExpect(jsonPath("$[0].motivo").value("Rotura de caja"))
                .andExpect(jsonPath("$[0].saldoResultante").value(990));

        postCon(almacenero, "/api/inventario/ajustes", Map.of("tipo", "SALIDA", "motivo", "Perdida total",
                "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 5000))))
                .andExpect(status().isConflict());
        postCon(almacenero, "/api/inventario/ajustes", Map.of("tipo", "ENTRADA",
                "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 1))))
                .andExpect(status().isBadRequest());
        postCon(almacenero, "/api/inventario/ajustes", Map.of("ubicacionId", datos.tienda1.getId(), "tipo", "ENTRADA",
                "motivo", "Conteo fisico", "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 1))))
                .andExpect(status().isForbidden());
        postCon(almacenero, "/api/inventario/ajustes", Map.of("tipo", "ENTRADA", "motivo", "Conteo fisico",
                "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 0.5))))
                .andExpect(status().isUnprocessableContent());
        postCon(almacenero, "/api/inventario/ajustes", Map.of("tipo", "ENTRADA", "motivo", "Numero enorme",
                "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 99999999999L))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("El inventario inicial solo se carga una vez por producto y ubicacion")
    void inventarioInicialUnaVez() throws Exception {
        postCon(datos.token(datos.admin), "/api/inventario/inicial", Map.of("ubicacionId", datos.almacen.getId(),
                "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 5))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Stock minimo y consulta de stock bajo, restringida a la propia ubicacion")
    void stockBajo() throws Exception {
        putCon(datos.token(datos.admin), "/api/inventario/stock/minimo", Map.of("productoId", perno.getId(),
                "ubicacionId", datos.almacen.getId(), "stockMinimo", 1000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bajo").value(true));

        getCon(datos.token(datos.almacenero), "/api/inventario/stock?soloBajo=true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].productoCodigo").value("PER-38"));
        getCon(datos.token(datos.almacenero), "/api/inventario/stock")
                .andExpect(jsonPath("$.totalElementos").value(2));
        getCon(datos.token(datos.vendedor1), "/api/inventario/stock")
                .andExpect(jsonPath("$.totalElementos").value(0));
        getCon(datos.token(datos.vendedor1), "/api/inventario/stock?ubicacionId={u}", datos.almacen.getId())
                .andExpect(status().isForbidden());
        getCon(datos.token(datos.vendedor1), "/api/inventario/kardex?ubicacionId={u}", datos.almacen.getId())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("No se puede anular una compra cuya mercaderia ya salio del almacen")
    void anularCompraConStockConsumido() throws Exception {
        Long proveedorId = datos.proveedor("20666666661").getId();
        Long compraId = leerId(postCon(datos.token(datos.almacenero), "/api/compras", Map.of(
                "empresaId", datos.empresa1.getId(), "proveedorId", proveedorId, "tipoComprobante", "BOLETA",
                "fechaEmision", hoyLima().toString(),
                "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 100, "precioUnitario", 0.3))))
                .andReturn(), "$.id");
        postCon(datos.token(datos.almacenero), "/api/traslados", Map.of("destinoId", datos.tienda1.getId(),
                "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 1050))))
                .andExpect(status().isCreated());

        postCon(datos.token(datos.admin), "/api/compras/{id}/anular", Map.of("motivo", "Devolucion al proveedor"),
                compraId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"));
    }
}
