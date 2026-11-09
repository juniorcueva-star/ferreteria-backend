package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.enums.TipoUbicacion;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.repository.UbicacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Punto 1 del encargado: el vendedor ve el stock de todas las ubicaciones en una sola consulta, solo para leer.
 */
class DisponibilidadIntegrationTest extends IntegracionTestBase {

    @Autowired private UbicacionRepository ubicacionRepository;

    private Producto perno;
    private Producto tuerca;
    private String vendedor1;

    @BeforeEach
    void preparar() throws Exception {
        perno = datos.producto("PER-HEX", UnidadBase.UNIDAD, "0.50");
        tuerca = datos.producto("TUE-1", UnidadBase.UNIDAD, "0.20");
        cargarStock(perno, datos.tienda1, 10);
        cargarStock(perno, datos.tienda2, 25);
        cargarStock(perno, datos.almacen, 300);
        vendedor1 = datos.token(datos.vendedor1);
    }

    @Test
    @DisplayName("El vendedor de la tienda 1 ve la tienda 2 y el almacen; el total es la suma y lo vacio sale en 0")
    void veTodasLasUbicaciones() throws Exception {
        getCon(vendedor1, "/api/inventario/resumen?productoIds={a},{b}", perno.getId(), tuerca.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].productoCodigo").value("PER-HEX"))
                .andExpect(jsonPath("$.contenido[0].ubicaciones.length()").value(3))
                // tiendas primero, luego el almacen
                .andExpect(jsonPath("$.contenido[0].ubicaciones[0].ubicacionNombre").value("Tienda 1"))
                .andExpect(jsonPath("$.contenido[0].ubicaciones[0].cantidad").value(10))
                .andExpect(jsonPath("$.contenido[0].ubicaciones[1].cantidad").value(25))
                .andExpect(jsonPath("$.contenido[0].ubicaciones[2].tipo").value("ALMACEN"))
                .andExpect(jsonPath("$.contenido[0].ubicaciones[2].cantidad").value(300))
                .andExpect(jsonPath("$.contenido[0].total").value(335))
                .andExpect(jsonPath("$.contenido[1].productoCodigo").value("TUE-1"))
                .andExpect(jsonPath("$.contenido[1].ubicaciones[0].cantidad").value(0))
                .andExpect(jsonPath("$.contenido[1].total").value(0));
    }

    @Test
    @DisplayName("Busca por palabras, marca el stock minimo y no muestra ubicaciones inactivas")
    void busquedaYMinimo() throws Exception {
        putCon(datos.token(datos.admin), "/api/inventario/stock/minimo", Map.of("productoId", perno.getId(),
                "ubicacionId", datos.tienda2.getId(), "stockMinimo", 30)).andExpect(status().isOk());
        Ubicacion cerrada = new Ubicacion();
        cerrada.setNombre("Tienda cerrada");
        cerrada.setTipo(TipoUbicacion.TIENDA);
        cerrada.setEmpresa(datos.empresa1);
        cerrada.setActivo(false);
        ubicacionRepository.save(cerrada);

        getCon(vendedor1, "/api/inventario/resumen?texto=per")
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].ubicaciones.length()").value(3))
                .andExpect(jsonPath("$.contenido[0].ubicaciones[1].bajo").value(true))
                .andExpect(jsonPath("$.contenido[0].ubicaciones[0].bajo").value(false));
    }

    @Test
    @DisplayName("Mas de 100 productos responde 422")
    void maximoProductos() throws Exception {
        String ids = LongStream.rangeClosed(1, 101).mapToObj(String::valueOf).collect(Collectors.joining(","));
        getCon(vendedor1, "/api/inventario/resumen?productoIds=" + ids).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Ver la tienda 2 no permite operarla: caja, traslado, ajuste y stock detallado siguen bloqueados")
    void soloLectura() throws Exception {
        Long tienda2 = datos.tienda2.getId();
        postCon(vendedor1, "/api/cajas/abrir", Map.of("ubicacionId", tienda2, "montoApertura", 0))
                .andExpect(status().isForbidden());
        postCon(vendedor1, "/api/traslados", Map.of("origenId", tienda2, "destinoId", datos.tienda1.getId(),
                "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 5))))
                .andExpect(status().isForbidden());
        postCon(vendedor1, "/api/inventario/ajustes", Map.of("ubicacionId", tienda2, "tipo", "SALIDA",
                "motivo", "Intento indebido", "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 1))))
                .andExpect(status().isForbidden());
        getCon(vendedor1, "/api/inventario/stock?ubicacionId={u}", tienda2).andExpect(status().isForbidden());

        // La venta siempre sale de la tienda de su caja: vende de la tienda 1 y la tienda 2 no cambia
        postCon(vendedor1, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        postCon(vendedor1, "/api/ventas", Map.of("condicion", "CONTADO",
                "detalles", List.of(Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 12)),
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 6))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"));
        getCon(vendedor1, "/api/inventario/resumen?productoIds={a}", perno.getId())
                .andExpect(jsonPath("$.contenido[0].ubicaciones[1].cantidad").value(25));
    }
}
