package com.ferreteria.service;

import com.ferreteria.dto.caja.CajaResponse;
import com.ferreteria.dto.caja.CierreCajaRequest;
import com.ferreteria.dto.caja.CobroPorMetodo;
import com.ferreteria.entity.CajaSesion;
import com.ferreteria.entity.enums.EstadoCaja;
import com.ferreteria.entity.enums.EstadoVenta;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.entity.enums.TipoPago;
import com.ferreteria.exception.AccesoDenegadoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.CajaSesionRepository;
import com.ferreteria.repository.PagoRepository;
import com.ferreteria.repository.VentaRepository;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.security.UsuarioActual;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CajaServiceTest {

    @Mock private CajaSesionRepository cajaRepository;
    @Mock private PagoRepository pagoRepository;
    @Mock private VentaRepository ventaRepository;
    @Mock private AccesoUbicacionService accesoUbicacion;
    @Mock private Buscador buscador;
    @Mock private Calendario calendario;
    @InjectMocks private CajaService cajaService;

    private final CajaSesion caja = Fabrica.caja(1L, Fabrica.tienda(1L), Fabrica.usuario(2L, Rol.VENDEDOR), "100.00");

    @Test
    @DisplayName("Cierre: esperado = apertura + efectivo; diferencia = contado - esperado; credito = vendido - cobrado al vender")
    void cuadre() {
        when(cajaRepository.bloquear(1L)).thenReturn(Optional.of(caja));
        when(accesoUbicacion.usuarioActual()).thenReturn(new UsuarioActual(2L, "v", "V", Rol.VENDEDOR, 1L));
        when(pagoRepository.cobrosPorMetodo(1L)).thenReturn(List.of(
                new CobroPorMetodo("EFECTIVO", "Efectivo", true, new BigDecimal("50.00")),
                new CobroPorMetodo("YAPE", "Yape", false, new BigDecimal("30.00"))));
        when(ventaRepository.sumarTotalEmitidas(1L)).thenReturn(new BigDecimal("100.00"));
        when(ventaRepository.countByCajaSesionIdAndEstado(1L, EstadoVenta.EMITIDA)).thenReturn(3L);
        when(pagoRepository.sumarPorTipo(1L, TipoPago.VENTA)).thenReturn(new BigDecimal("70.00"));
        when(pagoRepository.sumarPorTipo(1L, TipoPago.ABONO)).thenReturn(new BigDecimal("10.00"));

        CajaResponse cerrada = cajaService.cerrar(1L, new CierreCajaRequest(new BigDecimal("148.00")));

        assertThat(cerrada.estado()).isEqualTo(EstadoCaja.CERRADA);
        assertThat(cerrada.efectivoEsperado()).isEqualByComparingTo("150.00");
        assertThat(cerrada.diferencia()).isEqualByComparingTo("-2.00");
        assertThat(cerrada.resumen().totalCredito()).isEqualByComparingTo("30.00");
        assertThat(cerrada.resumen().totalCobrado()).isEqualByComparingTo("80.00");
        assertThat(cerrada.fechaCierre()).isNotNull();
    }

    @Test
    @DisplayName("Un vendedor no puede cerrar la caja de otro")
    void cajaAjena() {
        when(cajaRepository.bloquear(1L)).thenReturn(Optional.of(caja));
        when(accesoUbicacion.usuarioActual()).thenReturn(new UsuarioActual(3L, "otro", "O", Rol.VENDEDOR, 1L));

        assertThatThrownBy(() -> cajaService.cerrar(1L, new CierreCajaRequest(BigDecimal.ZERO)))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    @DisplayName("Una caja cerrada no se vuelve a cerrar")
    void yaCerrada() {
        caja.setEstado(EstadoCaja.CERRADA);
        when(cajaRepository.bloquear(1L)).thenReturn(Optional.of(caja));
        when(accesoUbicacion.usuarioActual()).thenReturn(new UsuarioActual(1L, "admin", "A", Rol.ADMIN, null));

        assertThatThrownBy(() -> cajaService.cerrar(1L, new CierreCajaRequest(BigDecimal.ZERO)))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
