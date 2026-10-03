package com.ferreteria.service;

import com.ferreteria.dto.ventas.AbonoRequest;
import com.ferreteria.dto.ventas.PagoRequest;
import com.ferreteria.entity.CajaSesion;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.Venta;
import com.ferreteria.entity.enums.CondicionVenta;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.entity.enums.TipoPago;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.VentaRepository;
import com.ferreteria.security.AccesoUbicacionService;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiadoServiceTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private AccesoUbicacionService accesoUbicacion;
    @Mock private Buscador buscador;
    @Mock private Cobros cobros;
    @Mock private Calendario calendario;
    @InjectMocks private FiadoService fiadoService;

    private final Ubicacion tienda = Fabrica.tienda(1L);
    private final Usuario vendedor = Fabrica.usuario(2L, Rol.VENDEDOR);
    private final CajaSesion caja = Fabrica.caja(5L, tienda, vendedor, "0");

    @Test
    @DisplayName("Un abono descuenta del saldo pendiente")
    void abonoParcial() {
        Venta venta = fiado(tienda, "100.00");
        when(ventaRepository.bloquear(1L)).thenReturn(Optional.of(venta));
        when(cobros.cajaAbiertaDelUsuario()).thenReturn(caja);
        when(buscador.usuarioActual()).thenReturn(vendedor);
        when(cobros.crearPagos(anyList(), eq(TipoPago.ABONO), eq(caja), eq(vendedor)))
                .thenReturn(List.of(Fabrica.pago("EFECTIVO", true, "30.00", caja, vendedor)));

        var respuesta = fiadoService.registrarAbono(1L, abono());

        assertThat(respuesta.saldoPendiente()).isEqualByComparingTo("70.00");
        assertThat(venta.getPagos()).hasSize(1);
    }

    @Test
    @DisplayName("Un abono mayor al saldo se rechaza")
    void abonoExcesivo() {
        when(ventaRepository.bloquear(1L)).thenReturn(Optional.of(fiado(tienda, "20.00")));
        when(cobros.cajaAbiertaDelUsuario()).thenReturn(caja);
        when(buscador.usuarioActual()).thenReturn(vendedor);
        when(cobros.crearPagos(anyList(), eq(TipoPago.ABONO), eq(caja), eq(vendedor)))
                .thenReturn(List.of(Fabrica.pago("EFECTIVO", true, "30.00", caja, vendedor)));

        assertThatThrownBy(() -> fiadoService.registrarAbono(1L, abono()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("supera el saldo");
    }

    @Test
    @DisplayName("El abono debe cobrarse en una caja de la misma tienda de la venta")
    void otraTienda() {
        when(ventaRepository.bloquear(1L)).thenReturn(Optional.of(fiado(Fabrica.tienda(2L), "50.00")));
        when(cobros.cajaAbiertaDelUsuario()).thenReturn(caja);

        assertThatThrownBy(() -> fiadoService.registrarAbono(1L, abono()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("tienda");
    }

    @Test
    @DisplayName("No se abona a una venta al contado")
    void ventaContado() {
        Venta venta = fiado(tienda, "0");
        venta.setCondicion(CondicionVenta.CONTADO);
        when(ventaRepository.bloquear(1L)).thenReturn(Optional.of(venta));

        assertThatThrownBy(() -> fiadoService.registrarAbono(1L, abono())).isInstanceOf(ReglaNegocioException.class);
    }

    private Venta fiado(Ubicacion ubicacion, String saldo) {
        Venta venta = new Venta();
        venta.setId(1L);
        venta.setUbicacion(ubicacion);
        venta.setEmpresa(ubicacion.getEmpresa());
        venta.setUsuario(vendedor);
        venta.setCajaSesion(caja);
        venta.setCondicion(CondicionVenta.CREDITO);
        venta.setSerie("NV01");
        venta.setNumero(1);
        venta.setTotal(new BigDecimal("100.00"));
        venta.setSubtotal(new BigDecimal("84.75"));
        venta.setIgv(new BigDecimal("15.25"));
        venta.setSaldoPendiente(new BigDecimal(saldo));
        return venta;
    }

    private static AbonoRequest abono() {
        return new AbonoRequest(List.of(new PagoRequest("EFECTIVO", new BigDecimal("30.00"), null)));
    }
}
