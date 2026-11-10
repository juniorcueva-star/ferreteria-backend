package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pedidos de reposicion: la tienda pide lo que se le acaba y el almacen lo atiende con un traslado. */
class ReposicionIntegrationTest extends IntegracionTestBase {

    private Producto perno;
    private String vendedor;
    private String almacenero;

    @BeforeEach
    void preparar() throws Exception {
        perno = datos.producto("PER-1", UnidadBase.UNIDAD, "0.50");
        cargarStock(perno, datos.almacen, 100);
        cargarStock(perno, datos.tienda1, 3);
        putCon(datos.token(datos.admin), "/api/inventario/stock/minimo", Map.of("productoId", perno.getId(),
                "ubicacionId", datos.tienda1.getId(), "stockMinimo", 5)).andExpect(status().isOk());
        vendedor = datos.token(datos.vendedor1);
        almacenero = datos.token(datos.almacenero);
    }

    private Long pedir(int cantidad) throws Exception {
        return leerId(postCon(vendedor, "/api/solicitudes-reposicion", Map.of("observacion", "Se acaban",
                        "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", cantidad))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(matchesPattern("SR-\\d{6}")))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn(), "$.id");
    }

    @Test
    @DisplayName("Sugiere hasta el doble del minimo; el almacen atiende con menos y el traslado llega a la tienda")
    void pedirYAtender() throws Exception {
        getCon(vendedor, "/api/solicitudes-reposicion/sugerencias")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cantidadSugerida").value(7))   // 2 x 5 - 3
                .andExpect(jsonPath("$[0].stockAlmacen").value(100));
        Long id = pedir(7);

        getCon(datos.token(datos.vendedor2), "/api/solicitudes-reposicion/{id}", id).andExpect(status().isForbidden());
        getCon(datos.token(datos.vendedor2), "/api/solicitudes-reposicion").andExpect(jsonPath("$.contenido.length()").value(0));
        getCon(almacenero, "/api/solicitudes-reposicion?estado=PENDIENTE").andExpect(jsonPath("$.contenido.length()").value(1));
        postCon(vendedor, "/api/solicitudes-reposicion/{id}/atender", Map.of(), id).andExpect(status().isForbidden());

        Long trasladoId = leerId(postCon(almacenero, "/api/solicitudes-reposicion/{id}/atender", Map.of("detalles",
                        List.of(Map.of("productoId", perno.getId(), "cantidad", 5))), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDA"))
                .andExpect(jsonPath("$.trasladoCodigo").value(matchesPattern("TR-\\d{6}")))
                .andReturn(), "$.trasladoId");
        assertThat(datos.stock(perno, datos.almacen)).isEqualByComparingTo("95");
        postCon(vendedor, "/api/traslados/{id}/recibir", Map.of(), trasladoId).andExpect(status().isOk());
        assertThat(datos.stock(perno, datos.tienda1)).isEqualByComparingTo("8");

        postCon(almacenero, "/api/solicitudes-reposicion/{id}/atender", Map.of(), id)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("La tienda anula, el almacen rechaza con motivo y no se aceptan fracciones de unidades")
    void anularRechazarYValidar() throws Exception {
        Long anulado = pedir(4);
        postCon(vendedor, "/api/solicitudes-reposicion/{id}/anular", Map.of("motivo", "Ya llego mercaderia"), anulado)
                .andExpect(jsonPath("$.estado").value("ANULADA"));
        Long rechazado = pedir(4);
        postCon(almacenero, "/api/solicitudes-reposicion/{id}/rechazar", Map.of("motivo", "No hay stock suficiente"),
                rechazado)
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivo").value("No hay stock suficiente"));
        postCon(almacenero, "/api/solicitudes-reposicion/{id}/atender", Map.of(), rechazado)
                .andExpect(status().isUnprocessableContent());
        postCon(vendedor, "/api/solicitudes-reposicion", Map.of("detalles",
                        List.of(Map.of("productoId", perno.getId(), "cantidad", 1.5))))
                .andExpect(status().isUnprocessableContent());
        assertThat(datos.stock(perno, datos.almacen)).isEqualByComparingTo("100");
    }
}
