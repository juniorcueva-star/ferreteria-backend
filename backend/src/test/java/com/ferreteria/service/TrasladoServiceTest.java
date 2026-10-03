package com.ferreteria.service;

import com.ferreteria.dto.inventario.LineaProductoRequest;
import com.ferreteria.dto.inventario.TrasladoRequest;
import com.ferreteria.entity.Traslado;
import com.ferreteria.entity.enums.EstadoTraslado;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.TrasladoRepository;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.service.stock.MovimientoStockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrasladoServiceTest {

    @Mock private TrasladoRepository trasladoRepository;
    @Mock private MovimientoStockService movimientoStock;
    @Mock private AccesoUbicacionService accesoUbicacion;
    @Mock private Buscador buscador;
    @Mock private Calendario calendario;
    @InjectMocks private TrasladoService trasladoService;

    @Test
    @DisplayName("El origen y el destino de un traslado deben ser distintos")
    void mismoOrigenYDestino() {
        when(accesoUbicacion.ubicacionParaOperar(null)).thenReturn(1L);
        TrasladoRequest request = new TrasladoRequest(null, 1L, null,
                List.of(new LineaProductoRequest(1L, null, BigDecimal.ONE)));

        assertThatThrownBy(() -> trasladoService.enviar(request)).isInstanceOf(ReglaNegocioException.class);
        verify(movimientoStock, never()).aplicar(any(), any(), anyList(), any());
    }

    @Test
    @DisplayName("Un traslado ya recibido no se vuelve a recibir")
    void yaRecibido() {
        Traslado traslado = new Traslado();
        traslado.setCodigo("TR-000001");
        traslado.setDestino(Fabrica.tienda(2L));
        traslado.setEstado(EstadoTraslado.RECIBIDO);
        when(trasladoRepository.bloquear(1L)).thenReturn(Optional.of(traslado));

        assertThatThrownBy(() -> trasladoService.recibir(1L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("RECIBIDO");
        verify(movimientoStock, never()).aplicar(any(), any(), anyList(), any());
    }
}
