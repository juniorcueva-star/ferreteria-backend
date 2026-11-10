package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Productos frecuentes del punto de venta: los que estan en mas ventas de la tienda en los ultimos 30 dias. */
class FrecuentesIntegrationTest extends IntegracionTestBase {

    @Test
    @DisplayName("Ordena por numero de ventas, solo cuenta la tienda del vendedor y no muestra inactivos")
    void frecuentes() throws Exception {
        Producto clavo = datos.producto("CLA-1", UnidadBase.UNIDAD, "0.10");
        Producto perno = datos.producto("PER-1", UnidadBase.UNIDAD, "0.50");
        Producto martillo = datos.producto("MAR-1", UnidadBase.UNIDAD, "30.00");
        for (Producto p : List.of(clavo, perno, martillo)) {
            cargarStock(p, datos.tienda1, 100);
        }
        String vendedor = datos.token(datos.vendedor1);
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        // Perno en 3 ventas, clavo en 2 (aunque en mas cantidad), martillo en 1
        vender(vendedor, perno, 1, "0.50");
        vender(vendedor, perno, 1, "0.50");
        vender(vendedor, perno, 1, "0.50");
        vender(vendedor, clavo, 50, "5.00");
        vender(vendedor, clavo, 50, "5.00");
        vender(vendedor, martillo, 1, "30.00");

        getCon(vendedor, "/api/productos/frecuentes")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].codigo").value("PER-1"))
                .andExpect(jsonPath("$[1].codigo").value("CLA-1"))
                .andExpect(jsonPath("$[2].codigo").value("MAR-1"));
        getCon(datos.token(datos.vendedor2), "/api/productos/frecuentes").andExpect(jsonPath("$.length()").value(0));

        String admin = datos.token(datos.admin);
        getCon(admin, "/api/productos/frecuentes?ubicacionId={id}&limite=1", datos.tienda1.getId())
                .andExpect(jsonPath("$.length()").value(1));
        postCon(admin, "/api/productos/acciones-lote", Map.of("accion", "DESACTIVAR", "productoIds", List.of(perno.getId())))
                .andExpect(status().isOk());
        getCon(vendedor, "/api/productos/frecuentes").andExpect(jsonPath("$[0].codigo").value("CLA-1"));
    }

    private void vender(String token, Producto producto, int cantidad, String monto) throws Exception {
        postCon(token, "/api/ventas", Map.of("condicion", "CONTADO",
                "detalles", List.of(Map.of("presentacionId", datos.presentacionId(producto, 0), "cantidad", cantidad)),
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", monto)))).andExpect(status().isCreated());
    }
}
