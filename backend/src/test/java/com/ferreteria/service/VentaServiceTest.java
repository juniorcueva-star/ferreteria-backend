package com.ferreteria.service;

import com.ferreteria.dto.ventas.PagoRequest;
import com.ferreteria.dto.ventas.VentaDetalleRequest;
import com.ferreteria.dto.ventas.VentaRequest;
import com.ferreteria.dto.ventas.VentaResponse;
import com.ferreteria.entity.CajaSesion;
import com.ferreteria.entity.Cliente;
import com.ferreteria.entity.Pago;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.SerieCorrelativo;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.Venta;
import com.ferreteria.entity.enums.CondicionVenta;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.entity.enums.TipoDocumentoVenta;
import com.ferreteria.entity.enums.TipoPago;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.CajaSesionRepository;
import com.ferreteria.repository.ClienteRepository;
import com.ferreteria.repository.PresentacionRepository;
import com.ferreteria.repository.SerieCorrelativoRepository;
import com.ferreteria.repository.VentaRepository;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.service.stock.MovimientoStockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de calculo de la venta (totales, IGV, vuelto, saldo) sin base de datos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VentaServiceTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private PresentacionRepository presentacionRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private SerieCorrelativoRepository serieRepository;
    @Mock private CajaSesionRepository cajaRepository;
    @Mock private MovimientoStockService movimientoStock;
    @Mock private AccesoUbicacionService accesoUbicacion;
    @Mock private Buscador buscador;
    @Mock private Cobros cobros;
    @Mock private Calendario calendario;
    @InjectMocks private VentaService ventaService;

    private final Usuario vendedor = Fabrica.usuario(2L, Rol.VENDEDOR);
    private final CajaSesion caja = Fabrica.caja(9L, Fabrica.tienda(1L), vendedor, "0");
    /** Ciento de clavos: factor 100, S/ 8.00 */
    private final Presentacion ciento = Fabrica.presentacion(5L, "100", "8.00", UnidadBase.UNIDAD);

    @BeforeEach
    void configurar() {
        when(cobros.cajaAbiertaDelUsuario()).thenReturn(caja);
        when(buscador.usuarioActual()).thenReturn(vendedor);
        when(presentacionRepository.findByIdIn(anyList())).thenReturn(List.of(ciento));
        SerieCorrelativo serie = new SerieCorrelativo();
        serie.setSerie("NV01");
        serie.setUltimoNumero(7);
        when(serieRepository.existsByUbicacionIdAndTipoDocumento(1L, TipoDocumentoVenta.NOTA_VENTA)).thenReturn(true);
        when(serieRepository.findFirstByUbicacionIdAndTipoDocumentoOrderByIdAsc(1L, TipoDocumentoVenta.NOTA_VENTA))
                .thenReturn(Optional.of(serie));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Contado pagado de mas en efectivo: calcula IGV, vuelto, numero y guarda el pago neto")
    void contadoConVuelto() {
        pagos(Fabrica.pago("EFECTIVO", true, "20.00", caja, vendedor));

        VentaResponse venta = ventaService.registrar(request(CondicionVenta.CONTADO, null, "2", null));

        assertThat(venta.numeroDocumento()).isEqualTo("NV01-000008");
        assertThat(venta.total()).isEqualByComparingTo("16.00");
        assertThat(venta.subtotal()).isEqualByComparingTo("13.56");
        assertThat(venta.igv()).isEqualByComparingTo("2.44");
        assertThat(venta.vuelto()).isEqualByComparingTo("4.00");
        assertThat(venta.pagos()).singleElement().satisfies(p -> assertThat(p.monto()).isEqualByComparingTo("16.00"));
        assertThat(venta.detalles().getFirst().cantidadBase()).isEqualByComparingTo("200");
        verify(movimientoStock).aplicar(eq(caja.getUbicacion()), any(), anyList(), any());
    }

    @Test
    @DisplayName("Pago mixto: el vuelto sale solo del efectivo")
    void vueltoDelEfectivoEnPagoMixto() {
        pagos(Fabrica.pago("YAPE", false, "10.00", caja, vendedor), Fabrica.pago("EFECTIVO", true, "10.00", caja, vendedor));

        VentaResponse venta = ventaService.registrar(request(CondicionVenta.CONTADO, null, "2", null));

        assertThat(venta.vuelto()).isEqualByComparingTo("4.00");
        assertThat(venta.pagos()).extracting(p -> p.monto().toPlainString()).containsExactly("10.00", "6.00");
    }

    @Test
    @DisplayName("Pagar de mas con un medio digital no genera vuelto: se rechaza sin mover stock")
    void sinVueltoDigital() {
        pagos(Fabrica.pago("TARJETA", false, "20.00", caja, vendedor));

        assertThatThrownBy(() -> ventaService.registrar(request(CondicionVenta.CONTADO, null, "2", null)))
                .isInstanceOf(ReglaNegocioException.class);
        verify(movimientoStock, never()).aplicar(any(), any(), anyList(), any());
    }

    @Test
    @DisplayName("Credito con adelanto: el saldo pendiente es total - adelanto")
    void creditoConAdelanto() {
        Cliente cliente = new Cliente();
        cliente.setId(3L);
        cliente.setNombre("Cliente");
        when(clienteRepository.findById(3L)).thenReturn(Optional.of(cliente));
        pagos(Fabrica.pago("EFECTIVO", true, "5.00", caja, vendedor));

        VentaResponse venta = ventaService.registrar(request(CondicionVenta.CREDITO, 3L, "2", null));

        assertThat(venta.saldoPendiente()).isEqualByComparingTo("11.00");
        assertThat(venta.vuelto()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Un descuento mayor que el importe de la linea se rechaza")
    void descuentoExcesivo() {
        pagos();

        assertThatThrownBy(() -> ventaService.registrar(request(CondicionVenta.CONTADO, null, "1", "8.01")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("descuento");
    }

    @Test
    @DisplayName("Un producto por unidades admite fracciones de presentacion si el total en unidades es entero")
    void mediaCientoEsValido() {
        pagos(Fabrica.pago("EFECTIVO", true, "4.00", caja, vendedor));

        VentaResponse venta = ventaService.registrar(request(CondicionVenta.CONTADO, null, "0.5", null));

        assertThat(venta.detalles().getFirst().cantidadBase()).isEqualByComparingTo("50");
        assertThat(venta.total()).isEqualByComparingTo("4.00");
    }

    private void pagos(Pago... pagos) {
        when(cobros.crearPagos(anyList(), eq(TipoPago.VENTA), eq(caja), eq(vendedor))).thenReturn(List.of(pagos));
    }

    private static VentaRequest request(CondicionVenta condicion, Long clienteId, String cantidad, String descuento) {
        return new VentaRequest(condicion, clienteId, null,
                List.of(new VentaDetalleRequest(5L, new BigDecimal(cantidad),
                        descuento == null ? null : new BigDecimal(descuento))),
                List.of(new PagoRequest("EFECTIVO", BigDecimal.ONE, null)));
    }
}
