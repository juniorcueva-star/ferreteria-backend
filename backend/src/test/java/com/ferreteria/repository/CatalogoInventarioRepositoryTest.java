package com.ferreteria.repository;

import com.ferreteria.entity.Categoria;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Stock;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.enums.TipoUbicacion;
import com.ferreteria.entity.enums.UnidadBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba el mapeo de producto, presentacion y stock, y las reglas que protege la BD
 * (CHECK e indices parciales), por eso corre contra PostgreSQL real.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CatalogoInventarioRepositoryTest {

    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private UbicacionRepository ubicacionRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Guarda un producto con sus presentaciones en cascada")
    void guardaProductoConPresentaciones() {
        Producto producto = nuevoProducto("TEST-CLAVO");
        producto.agregarPresentacion(nuevaPresentacion("Unidad", "1", "0.10", true));
        producto.agregarPresentacion(nuevaPresentacion("Ciento", "100", "8.00", false));
        productoRepository.saveAndFlush(producto);
        entityManager.clear();

        Producto encontrado = productoRepository.findById(producto.getId()).orElseThrow();
        assertThat(encontrado.getPresentaciones()).extracting(Presentacion::getNombre)
                .containsExactly("Unidad", "Ciento");
        assertThat(encontrado.getCreatedAt()).isNotNull();
        assertThat(encontrado.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("La BD rechaza dos presentaciones principales del mismo producto (indice parcial)")
    void soloUnaPresentacionPrincipal() {
        Producto producto = nuevoProducto("TEST-PERNO");
        producto.agregarPresentacion(nuevaPresentacion("Unidad", "1", "0.50", true));
        producto.agregarPresentacion(nuevaPresentacion("Caja", "50", "20.00", true));

        assertThatThrownBy(() -> productoRepository.saveAndFlush(producto))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La BD rechaza stock negativo (CHECK ck_stock_no_negativo)")
    void stockNegativoEsRechazado() {
        Producto producto = productoRepository.saveAndFlush(nuevoProducto("TEST-CABLE"));
        Ubicacion almacen = new Ubicacion();
        almacen.setNombre("Almacen Prueba Stock");
        almacen.setTipo(TipoUbicacion.ALMACEN);
        ubicacionRepository.saveAndFlush(almacen);

        Stock stock = new Stock();
        stock.setProducto(producto);
        stock.setUbicacion(almacen);
        stock.setCantidad(new BigDecimal("-1.000"));

        assertThatThrownBy(() -> stockRepository.saveAndFlush(stock))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- datos de prueba ----------

    private Producto nuevoProducto(String codigo) {
        Categoria categoria = categoriaRepository.findAll().getFirst(); // cargadas por la migracion V2
        Producto producto = new Producto();
        producto.setCodigo(codigo);
        producto.setNombre("Producto " + codigo);
        producto.setCategoria(categoria);
        producto.setUnidadBase(UnidadBase.UNIDAD);
        return producto;
    }

    private Presentacion nuevaPresentacion(String nombre, String factor, String precio, boolean principal) {
        Presentacion presentacion = new Presentacion();
        presentacion.setNombre(nombre);
        presentacion.setFactor(new BigDecimal(factor));
        presentacion.setPrecioVenta(new BigDecimal(precio));
        presentacion.setPrincipal(principal);
        return presentacion;
    }
}
