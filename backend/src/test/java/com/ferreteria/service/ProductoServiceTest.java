package com.ferreteria.service;

import com.ferreteria.dto.catalogo.PresentacionRequest;
import com.ferreteria.dto.catalogo.ProductoCrearRequest;
import com.ferreteria.entity.Categoria;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.CategoriaRepository;
import com.ferreteria.repository.PresentacionRepository;
import com.ferreteria.repository.ProductoRepository;
import com.ferreteria.service.imagen.AlmacenImagenes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock private ProductoRepository productoRepository;
    @Mock private PresentacionRepository presentacionRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private AlmacenImagenes almacenImagenes;
    @InjectMocks private ProductoService productoService;

    @Test
    @DisplayName("Rechaza dos presentaciones principales antes de tocar la BD")
    void dosPrincipales() {
        ProductoCrearRequest request = request(UnidadBase.UNIDAD,
                presentacion("Unidad", "1", true), presentacion("Caja", "50", true));

        assertThatThrownBy(() -> productoService.crear(request))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("principal");
        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rechaza nombres de presentacion repetidos (sin importar mayusculas)")
    void nombresRepetidos() {
        ProductoCrearRequest request = request(UnidadBase.KILO,
                presentacion("Kilo", "1", false), presentacion("KILO", "1", false));

        assertThatThrownBy(() -> productoService.crear(request)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("Un producto por KILO si acepta presentaciones con fraccion (medio kilo)")
    void kiloAceptaFraccion() {
        Categoria categoria = new Categoria();
        categoria.setNombre("Clavos");
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = productoService.crear(request(UnidadBase.KILO,
                presentacion("Kilo", "1", false), presentacion("Medio kilo", "0.5", false)));

        assertThat(respuesta.presentaciones()).hasSize(2);
        assertThat(respuesta.presentaciones().get(0).principal()).isTrue();
        assertThat(respuesta.presentaciones().get(1).factor()).isEqualByComparingTo("0.5");
    }

    private static ProductoCrearRequest request(UnidadBase unidad, PresentacionRequest... presentaciones) {
        return new ProductoCrearRequest("P-1", "Producto", null, null, 1L, unidad, List.of(presentaciones));
    }

    private static PresentacionRequest presentacion(String nombre, String factor, boolean principal) {
        return new PresentacionRequest(nombre, new BigDecimal(factor), new BigDecimal("1.00"), null, principal, null);
    }
}
