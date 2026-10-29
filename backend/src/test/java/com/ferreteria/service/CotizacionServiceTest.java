package com.ferreteria.service;

import com.ferreteria.dto.cotizaciones.CotizacionDetalleRequest;
import com.ferreteria.dto.cotizaciones.CotizacionRequest;
import com.ferreteria.entity.*;
import com.ferreteria.entity.enums.EstadoCotizacion;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.exception.AccesoDenegadoException;
import com.ferreteria.repository.*;
import com.ferreteria.security.AccesoUbicacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CotizacionServiceTest {
    @Mock VentaRepository ventaRepository; @Mock CotizacionRepository cotizacionRepository; @Mock ClienteRepository clienteRepository;
    @Mock PresentacionRepository presentacionRepository; @Mock Buscador buscador; @Mock AccesoUbicacionService acceso;
    @InjectMocks CotizacionService service;
    private Cliente cliente; private Presentacion millar; private Ubicacion tienda;

    @BeforeEach void preparar() {
        cliente = new Cliente(); cliente.setId(2L); cliente.setNombre("Cliente"); cliente.setActivo(true);
        tienda = new Ubicacion(); tienda.setId(1L); tienda.setNombre("Tienda"); tienda.setActivo(true);
        Usuario usuario = new Usuario(); usuario.setId(3L); usuario.setUsername("vendedor");
        Producto producto = new Producto(); producto.setId(4L); producto.setNombre("Perno"); producto.setUnidadBase(UnidadBase.UNIDAD); producto.setActivo(true);
        millar = new Presentacion(); millar.setId(5L); millar.setProducto(producto); millar.setNombre("Millar"); millar.setFactor(new BigDecimal("1000")); millar.setPrecioVenta(new BigDecimal("70")); millar.setActivo(true);
        when(acceso.ubicacionParaOperar(any())).thenReturn(1L); when(buscador.ubicacionActiva(1L)).thenReturn(tienda); when(buscador.usuarioActual()).thenReturn(usuario);
        when(clienteRepository.findById(2L)).thenReturn(Optional.of(cliente)); when(presentacionRepository.findByIdIn(anyList())).thenReturn(List.of(millar)); when(cotizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test void creaBorradorConFraccionComercialYNoTocaStock() {
        var respuesta = service.crear(request("0.5", null, null));
        assertThat(respuesta.estado()).isEqualTo(EstadoCotizacion.BORRADOR);
        assertThat(respuesta.total()).isEqualByComparingTo("35.00");
        assertThat(respuesta.detalles().getFirst().cantidadBase()).isEqualByComparingTo("500");
        verify(cotizacionRepository).save(any(Cotizacion.class));
    }

    @Test void calculaDescuentoEIncrementoPorcentualYRechazaCombinacion() {
        assertThat(service.crear(request("1", null, "-10")).total()).isEqualByComparingTo("63.00");
        assertThat(service.crear(request("1", null, "10")).total()).isEqualByComparingTo("77.00");
        assertThatThrownBy(() -> service.crear(request("1", "1", "-5"))).isInstanceOf(ReglaNegocioException.class);
    }

    @Test void rechazaPresentacionInactivaYEmisionFueraDeBorrador() {
        millar.setActivo(false);
        assertThatThrownBy(() -> service.crear(request("1", null, null))).isInstanceOf(ReglaNegocioException.class);
        Cotizacion emitida = cotizacion(); emitida.setEstado(EstadoCotizacion.EMITIDA); when(cotizacionRepository.bloquear(9L)).thenReturn(Optional.of(emitida));
        assertThatThrownBy(() -> service.emitir(9L)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test void editarBorradorConservaSnapshotDeMismaPresentacion() {
        Cotizacion cotizacion = cotizacion(); CotizacionDetalle anterior = new CotizacionDetalle(); anterior.setPresentacion(millar); anterior.setProducto(millar.getProducto()); anterior.setFactor(new BigDecimal("900")); anterior.setPrecioUnitario(new BigDecimal("60")); cotizacion.agregarDetalle(anterior);
        when(cotizacionRepository.bloquear(9L)).thenReturn(Optional.of(cotizacion));
        var respuesta = service.actualizar(9L, request("2", null, null));
        assertThat(respuesta.detalles().getFirst().precioUnitario()).isEqualByComparingTo("60");
        assertThat(respuesta.detalles().getFirst().factor()).isEqualByComparingTo("900");
    }

    @Test void consultaIncluyeVentaVinculadaSoloTrasValidarAcceso() {
        Cotizacion c = cotizacion();
        Venta v = new Venta(); v.setId(12L); v.setSerie("NV01"); v.setNumero(7);
        v.setEstado(com.ferreteria.entity.enums.EstadoVenta.EMITIDA); v.setTotal(new BigDecimal("35.00"));
        when(cotizacionRepository.findById(9L)).thenReturn(Optional.of(c));
        when(ventaRepository.findByCotizacionId(9L)).thenReturn(Optional.of(v));
        var respuesta = service.obtener(9L);
        assertThat(respuesta.venta().id()).isEqualTo(12L);
        assertThat(respuesta.venta().numeroDocumento()).isEqualTo("NV01-000007");
        verify(acceso).validarAcceso(1L);
    }

    @Test void rechazaClienteInexistente() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());
        CotizacionRequest request = new CotizacionRequest(null, 99L, null,
                List.of(new CotizacionDetalleRequest(5L, BigDecimal.ONE, null, null)));
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(RuntimeException.class);
    }

    @Test void niegaConsultaDeOtraTienda() {
        Cotizacion cotizacion = cotizacion(); when(cotizacionRepository.findById(9L)).thenReturn(Optional.of(cotizacion));
        doThrow(new AccesoDenegadoException("Sin acceso")).when(acceso).validarAcceso(1L);
        assertThatThrownBy(() -> service.obtener(9L)).isInstanceOf(AccesoDenegadoException.class);
    }

    @Test void anulaBorradorYRechazaDobleAnulacion() {
        Cotizacion cotizacion = cotizacion(); when(cotizacionRepository.bloquear(9L)).thenReturn(Optional.of(cotizacion));
        assertThat(service.anular(9L).estado()).isEqualTo(EstadoCotizacion.ANULADA);
        assertThatThrownBy(() -> service.anular(9L)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test void presentacionNuevaUsaPrecioYFactorVigentes() {
        Presentacion nueva = new Presentacion(); nueva.setId(6L); nueva.setProducto(millar.getProducto()); nueva.setNombre("Ciento"); nueva.setFactor(new BigDecimal("100")); nueva.setPrecioVenta(new BigDecimal("8")); nueva.setActivo(true);
        Cotizacion cotizacion = cotizacion(); CotizacionDetalle anterior = new CotizacionDetalle(); anterior.setPresentacion(millar); anterior.setProducto(millar.getProducto()); anterior.setFactor(new BigDecimal("900")); anterior.setPrecioUnitario(new BigDecimal("60")); cotizacion.agregarDetalle(anterior);
        when(cotizacionRepository.bloquear(9L)).thenReturn(Optional.of(cotizacion)); when(presentacionRepository.findByIdIn(anyList())).thenReturn(List.of(nueva));
        CotizacionRequest request = new CotizacionRequest(null, 2L, null, List.of(new CotizacionDetalleRequest(6L, BigDecimal.ONE, null, null)));
        var respuesta = service.actualizar(9L, request);
        assertThat(respuesta.detalles().getFirst().precioUnitario()).isEqualByComparingTo("8");
        assertThat(respuesta.detalles().getFirst().factor()).isEqualByComparingTo("100");
    }

    private Cotizacion cotizacion() { Cotizacion c = new Cotizacion(); c.setId(9L); c.setUbicacion(tienda); c.setCliente(cliente); Usuario u = new Usuario(); u.setUsername("vendedor"); c.setUsuario(u); c.setEstado(EstadoCotizacion.BORRADOR); return c; }
    private static CotizacionRequest request(String cantidad, String descuento, String ajuste) { return new CotizacionRequest(null, 2L, null, List.of(new CotizacionDetalleRequest(5L, new BigDecimal(cantidad), descuento == null ? null : new BigDecimal(descuento), ajuste == null ? null : new BigDecimal(ajuste)))); }
}
