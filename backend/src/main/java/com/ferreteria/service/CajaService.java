package com.ferreteria.service;

import com.ferreteria.dto.caja.AperturaCajaRequest;
import com.ferreteria.dto.caja.CajaResponse;
import com.ferreteria.dto.caja.CierreCajaRequest;
import com.ferreteria.dto.caja.CobroPorMetodo;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.entity.CajaSesion;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.enums.EstadoCaja;
import com.ferreteria.entity.enums.EstadoVenta;
import com.ferreteria.entity.enums.TipoPago;
import com.ferreteria.entity.enums.TipoUbicacion;
import com.ferreteria.exception.AccesoDenegadoException;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.CajaSesionRepository;
import com.ferreteria.repository.Especificaciones;
import com.ferreteria.repository.PagoRepository;
import com.ferreteria.repository.VentaRepository;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.security.UsuarioActual;
import com.ferreteria.util.Montos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Caja por vendedor: se abre con un monto inicial, recibe los pagos de ventas y abonos, y se cierra con cuadre:
 * efectivo esperado = apertura + cobros en efectivo; diferencia = contado - esperado.
 */
@Service
@RequiredArgsConstructor
public class CajaService {

    private final CajaSesionRepository cajaRepository;
    private final PagoRepository pagoRepository;
    private final VentaRepository ventaRepository;
    private final AccesoUbicacionService accesoUbicacion;
    private final Buscador buscador;
    private final Calendario calendario;

    @Transactional
    public CajaResponse abrir(AperturaCajaRequest request) {
        UsuarioActual usuario = accesoUbicacion.usuarioActual();
        Ubicacion tienda = buscador.ubicacionActiva(accesoUbicacion.ubicacionParaOperar(request.ubicacionId()));
        if (tienda.getTipo() != TipoUbicacion.TIENDA) {
            throw new ReglaNegocioException("La caja solo se abre en una tienda");
        }
        cajaRepository.findByUsuarioIdAndEstado(usuario.id(), EstadoCaja.ABIERTA).ifPresent(c -> {
            throw new ReglaNegocioException("Ya tiene una caja abierta (id " + c.getId() + "). Cierrela primero");
        });
        CajaSesion caja = new CajaSesion();
        caja.setUbicacion(tienda);
        caja.setUsuario(buscador.usuarioActual());
        caja.setMontoApertura(Montos.dinero(request.montoApertura()));
        cajaRepository.saveAndFlush(caja); // el indice unico parcial impide dos cajas abiertas aunque lleguen juntas
        return CajaResponse.desde(caja, resumen(caja));
    }

    /** Caja abierta del usuario conectado, con su resumen en vivo. */
    @Transactional(readOnly = true)
    public CajaResponse obtenerActual() {
        CajaSesion caja = cajaRepository.findByUsuarioIdAndEstado(accesoUbicacion.usuarioActual().id(),
                        EstadoCaja.ABIERTA)
                .orElseThrow(() -> new RecursoNoEncontradoException("No tiene una caja abierta"));
        return CajaResponse.desde(caja, resumen(caja));
    }

    @Transactional(readOnly = true)
    public CajaResponse obtener(Long id) {
        CajaSesion caja = cajaRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Caja", id));
        accesoUbicacion.validarAcceso(caja.getUbicacion().getId());
        return CajaResponse.desde(caja, resumen(caja));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<CajaResponse> listar(Long ubicacionId, Long usuarioId, EstadoCaja estado, LocalDate desde,
                                               LocalDate hasta, Pageable pageable) {
        calendario.validarRango(desde, hasta);
        Specification<CajaSesion> filtro = Specification.allOf(
                Especificaciones.igual("ubicacion.id", accesoUbicacion.ubicacionParaConsultar(ubicacionId)),
                Especificaciones.igual("usuario.id", usuarioId),
                Especificaciones.igual("estado", estado),
                Especificaciones.desde("fechaApertura", calendario.inicio(desde)),
                Especificaciones.antesDe("fechaApertura", calendario.finExclusivo(hasta)));
        return PaginaResponse.de(cajaRepository.findAll(filtro, pageable), c -> CajaResponse.desde(c, null));
    }

    /**
     * Cierra la caja con bloqueo exclusivo: si hay una venta o abono en curso en esta caja, el cierre espera a que
     * termine, para que el efectivo esperado incluya todo lo cobrado. Solo la cierra su duenio o el ADMIN.
     */
    @Transactional
    public CajaResponse cerrar(Long id, CierreCajaRequest request) {
        CajaSesion caja = cajaRepository.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Caja", id));
        UsuarioActual usuario = accesoUbicacion.usuarioActual();
        if (!usuario.esAdmin() && !caja.getUsuario().getId().equals(usuario.id())) {
            throw new AccesoDenegadoException("Solo puede cerrar su propia caja");
        }
        if (caja.getEstado() == EstadoCaja.CERRADA) {
            throw new ReglaNegocioException("La caja ya esta cerrada");
        }
        CajaResponse.Resumen resumen = resumen(caja);
        BigDecimal contado = Montos.dinero(request.efectivoContado());
        caja.setEfectivoEsperado(resumen.efectivoEsperado());
        caja.setEfectivoContado(contado);
        caja.setDiferencia(contado.subtract(resumen.efectivoEsperado()));
        caja.setFechaCierre(OffsetDateTime.now());
        caja.setEstado(EstadoCaja.CERRADA);
        return CajaResponse.desde(caja, resumen);
    }

    private CajaResponse.Resumen resumen(CajaSesion caja) {
        List<CobroPorMetodo> cobros = pagoRepository.cobrosPorMetodo(caja.getId());
        BigDecimal totalCobrado = cobros.stream().map(CobroPorMetodo::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal efectivo = cobros.stream().filter(CobroPorMetodo::efectivo).map(CobroPorMetodo::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVendido = ventaRepository.sumarTotalEmitidas(caja.getId());
        BigDecimal cobradoEnVentas = pagoRepository.sumarPorTipo(caja.getId(), TipoPago.VENTA);
        BigDecimal abonos = pagoRepository.sumarPorTipo(caja.getId(), TipoPago.ABONO);
        return new CajaResponse.Resumen(
                ventaRepository.countByCajaSesionIdAndEstado(caja.getId(), EstadoVenta.EMITIDA),
                Montos.dinero(totalVendido),
                Montos.dinero(totalVendido.subtract(cobradoEnVentas)),
                Montos.dinero(totalCobrado),
                Montos.dinero(abonos),
                cobros,
                Montos.dinero(caja.getMontoApertura().add(efectivo)));
    }
}
