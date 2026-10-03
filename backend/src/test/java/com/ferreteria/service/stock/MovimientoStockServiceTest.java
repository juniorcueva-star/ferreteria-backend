package com.ferreteria.service.stock;

import com.ferreteria.entity.MovimientoInventario;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Stock;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.enums.TipoMovimiento;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.exception.StockInsuficienteException;
import com.ferreteria.repository.MovimientoInventarioRepository;
import com.ferreteria.repository.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovimientoStockServiceTest {

    @Mock private StockRepository stockRepository;
    @Mock private MovimientoInventarioRepository movimientoRepository;
    @InjectMocks private MovimientoStockService service;

    private final Ubicacion tienda = ubicacion();
    private final OrigenMovimiento origen = OrigenMovimiento.ajuste(new Usuario(), "prueba");

    @Test
    @DisplayName("Una salida mayor al stock lanza StockInsuficiente y no toca nada")
    void salidaSinStock() {
        Producto perno = producto(1L, UnidadBase.UNIDAD);
        Stock stock = stock("5");
        when(stockRepository.bloquear(1L, 10L)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> service.aplicar(tienda, TipoMovimiento.VENTA,
                List.of(LineaMovimiento.de(perno, new BigDecimal("6"))), origen))
                .isInstanceOf(StockInsuficienteException.class);
        assertThat(stock.getCantidad()).isEqualByComparingTo("5");
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una salida resta, guarda el kardex con cantidad negativa y el saldo resultante")
    void salidaRestaYRegistraKardex() {
        Producto cable = producto(1L, UnidadBase.METRO);
        Stock stock = stock("100");
        when(stockRepository.bloquear(1L, 10L)).thenReturn(Optional.of(stock));
        when(movimientoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<MovimientoInventario> movimientos = service.aplicar(tienda, TipoMovimiento.VENTA,
                List.of(LineaMovimiento.de(cable, new BigDecimal("12.5"))), origen);

        assertThat(stock.getCantidad()).isEqualByComparingTo("87.5");
        assertThat(movimientos.getFirst().getCantidad()).isEqualByComparingTo("-12.5");
        assertThat(movimientos.getFirst().getSaldoResultante()).isEqualByComparingTo("87.5");
    }

    @Test
    @DisplayName("Las lineas del mismo producto se suman y los bloqueos van ordenados por id (evita deadlocks)")
    void agrupaYOrdena() {
        Producto p2 = producto(2L, UnidadBase.UNIDAD);
        Producto p1 = producto(1L, UnidadBase.UNIDAD);
        Stock s1 = stock("10");
        Stock s2 = stock("10");
        when(stockRepository.bloquear(1L, 10L)).thenReturn(Optional.of(s1));
        when(stockRepository.bloquear(2L, 10L)).thenReturn(Optional.of(s2));

        service.aplicar(tienda, TipoMovimiento.VENTA, List.of(
                LineaMovimiento.de(p2, new BigDecimal("1")),
                LineaMovimiento.de(p1, new BigDecimal("2")),
                LineaMovimiento.de(p2, new BigDecimal("3"))), origen);

        InOrder orden = inOrder(stockRepository);
        orden.verify(stockRepository).bloquear(1L, 10L);
        orden.verify(stockRepository).bloquear(2L, 10L);
        assertThat(s2.getCantidad()).isEqualByComparingTo("6");
        assertThat(s1.getCantidad()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("Una entrada crea la fila de stock si no existia")
    void entradaCreaFila() {
        Producto p = producto(1L, UnidadBase.KILO);
        Stock stock = stock("0");
        when(stockRepository.bloquear(1L, 10L)).thenReturn(Optional.of(stock));

        service.aplicar(tienda, TipoMovimiento.COMPRA, List.of(LineaMovimiento.de(p, new BigDecimal("2.5"))), origen);

        verify(stockRepository).crearSiNoExiste(1L, 10L);
        assertThat(stock.getCantidad()).isEqualByComparingTo("2.5");
    }

    @Test
    @DisplayName("Un producto por unidades no puede moverse en fracciones")
    void unidadSinFracciones() {
        Producto perno = producto(1L, UnidadBase.UNIDAD);

        assertThatThrownBy(() -> service.aplicar(tienda, TipoMovimiento.COMPRA,
                List.of(LineaMovimiento.de(perno, new BigDecimal("1.5"))), origen))
                .isInstanceOf(ReglaNegocioException.class);
    }

    private static Producto producto(Long id, UnidadBase unidad) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre("Producto " + id);
        producto.setUnidadBase(unidad);
        return producto;
    }

    private static Stock stock(String cantidad) {
        Stock stock = new Stock();
        stock.setCantidad(new BigDecimal(cantidad));
        return stock;
    }

    private static Ubicacion ubicacion() {
        Ubicacion ubicacion = new Ubicacion();
        ubicacion.setId(10L);
        ubicacion.setNombre("Tienda");
        return ubicacion;
    }
}
