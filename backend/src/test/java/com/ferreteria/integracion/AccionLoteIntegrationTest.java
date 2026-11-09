package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.repository.CategoriaRepository;
import com.ferreteria.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acciones en lote sobre productos: asignar categoria, activar y desactivar. Todo o nada.
 */
class AccionLoteIntegrationTest extends IntegracionTestBase {

    @Autowired private ProductoRepository productoRepository;
    @Autowired private CategoriaRepository categoriaRepository;

    private Producto perno;
    private Producto tuerca;
    private Long categoriaPernos;

    @BeforeEach
    void preparar() {
        perno = datos.producto("PER-1", UnidadBase.UNIDAD, "0.50");
        tuerca = datos.producto("TUE-1", UnidadBase.UNIDAD, "0.20");
        categoriaPernos = categoriaRepository.findAll().stream()
                .filter(c -> c.getNombre().equals("Pernos")).findFirst().orElseThrow().getId(); // creada en V9
    }

    @Test
    @DisplayName("Asigna categoria y luego desactiva y activa varios productos")
    void exito() throws Exception {
        String admin = datos.token(datos.admin);
        postCon(admin, "/api/productos/acciones-lote", Map.of("productoIds", List.of(perno.getId(), tuerca.getId()),
                "accion", "ASIGNAR_CATEGORIA", "categoriaId", categoriaPernos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productosActualizados").value(2));
        assertThat(productoRepository.findAll()).allMatch(p -> p.getCategoria().getId().equals(categoriaPernos));

        postCon(admin, "/api/productos/acciones-lote", Map.of("productoIds", List.of(perno.getId(), tuerca.getId(),
                perno.getId()), "accion", "DESACTIVAR")).andExpect(jsonPath("$.productosActualizados").value(2));
        assertThat(productoRepository.findAll()).noneMatch(Producto::isActivo);

        postCon(admin, "/api/productos/acciones-lote", Map.of("productoIds", List.of(tuerca.getId()),
                "accion", "ACTIVAR")).andExpect(status().isOk());
        assertThat(productoRepository.findById(tuerca.getId()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    @DisplayName("Si un producto no existe responde 422 y no cambia ninguno")
    void productoInexistente() throws Exception {
        postCon(datos.token(datos.admin), "/api/productos/acciones-lote", Map.of(
                "productoIds", List.of(perno.getId(), 999999), "accion", "DESACTIVAR"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value("No existen los productos [999999]: no se cambio ninguno"));
        assertThat(productoRepository.findAll()).allMatch(Producto::isActivo);
    }

    @Test
    @DisplayName("Lista vacia o de mas de 200 productos responde 400; sin categoria responde 422")
    void validaciones() throws Exception {
        String admin = datos.token(datos.admin);
        postCon(admin, "/api/productos/acciones-lote", Map.of("productoIds", List.of(), "accion", "ACTIVAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        postCon(admin, "/api/productos/acciones-lote", Map.of(
                "productoIds", LongStream.rangeClosed(1, 201).boxed().toList(), "accion", "ACTIVAR"))
                .andExpect(status().isBadRequest());
        postCon(admin, "/api/productos/acciones-lote", Map.of("productoIds", List.of(perno.getId()),
                "accion", "ASIGNAR_CATEGORIA")).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Solo el ADMIN puede usar acciones en lote")
    void soloAdmin() throws Exception {
        postCon(datos.token(datos.vendedor1), "/api/productos/acciones-lote", Map.of(
                "productoIds", List.of(perno.getId()), "accion", "DESACTIVAR"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Las unidades de medida informan decimales y factor sugerido")
    void unidadesDeMedida() throws Exception {
        getCon(datos.token(datos.vendedor1), "/api/unidades-medida")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'DOCENA')].factorSugerido").value(12.0))
                .andExpect(jsonPath("$[?(@.codigo == 'DOCENA')].decimales").value(0))
                .andExpect(jsonPath("$[?(@.codigo == 'KILO')].decimales").value(3));
    }
}
