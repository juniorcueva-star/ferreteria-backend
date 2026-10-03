package com.ferreteria.service;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.fiado.DeudorResponse;
import com.ferreteria.dto.ventas.AbonoRequest;
import com.ferreteria.dto.ventas.VentaResponse;
import com.ferreteria.entity.CajaSesion;
import com.ferreteria.entity.Pago;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.Venta;
import com.ferreteria.entity.enums.CondicionVenta;
import com.ferreteria.entity.enums.EstadoVenta;
import com.ferreteria.entity.enums.TipoPago;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.Especificaciones;
import com.ferreteria.repository.VentaRepository;
import com.ferreteria.security.AccesoUbicacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ventas al fiado: abonos (pagos parciales posteriores) y consultas de deudas y deudores.
 */
@Service
@RequiredArgsConstructor
public class FiadoService {

    private final VentaRepository ventaRepository;
    private final AccesoUbicacionService accesoUbicacion;
    private final Buscador buscador;
    private final Cobros cobros;
    private final Calendario calendario;

    /**
     * Registra un abono en la caja abierta del usuario. El abono se cobra en la misma tienda que hizo la venta
     * (cada tienda tiene su RUC y su dinero) y no puede superar el saldo pendiente.
     * Bloqueos: primero la venta (para que dos abonos simultaneos no paguen de mas) y luego la caja.
     */
    @Transactional
    public VentaResponse registrarAbono(Long ventaId, AbonoRequest request) {
        Venta venta = ventaRepository.bloquear(ventaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta", ventaId));
        accesoUbicacion.validarAcceso(venta.getUbicacion().getId());
        if (venta.getCondicion() != CondicionVenta.CREDITO || venta.getEstado() != EstadoVenta.EMITIDA) {
            throw new ReglaNegocioException("Solo se abona a ventas al credito vigentes");
        }
        if (venta.getSaldoPendiente().signum() == 0) {
            throw new ReglaNegocioException("La venta " + venta.getNumeroDocumento() + " ya esta pagada");
        }
        CajaSesion caja = cobros.cajaAbiertaDelUsuario();
        if (!caja.getUbicacion().getId().equals(venta.getUbicacion().getId())) {
            throw new ReglaNegocioException("El abono se cobra en la tienda donde se hizo la venta ("
                    + venta.getUbicacion().getNombre() + ")");
        }
        Usuario usuario = buscador.usuarioActual();
        List<Pago> pagos = cobros.crearPagos(request.pagos(), TipoPago.ABONO, caja, usuario);
        BigDecimal abonado = pagos.stream().map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (abonado.compareTo(venta.getSaldoPendiente()) > 0) {
            throw new ReglaNegocioException("El abono (" + abonado.toPlainString() + ") supera el saldo pendiente ("
                    + venta.getSaldoPendiente().toPlainString() + ")");
        }
        pagos.forEach(venta::agregarPago);
        venta.setSaldoPendiente(venta.getSaldoPendiente().subtract(abonado));
        ventaRepository.flush(); // asigna id a los pagos nuevos para la respuesta
        return VentaResponse.completa(venta);
    }

    /**
     * Ventas al fiado con saldo pendiente.
     *
     * @param soloVencidas true = solo las que tienen fecha de vencimiento anterior a hoy
     */
    @Transactional(readOnly = true)
    public PaginaResponse<VentaResponse> listarDeudas(Long ubicacionId, Long clienteId, boolean soloVencidas,
                                                      Pageable pageable) {
        Specification<Venta> filtro = Specification.allOf(
                Especificaciones.igual("ubicacion.id", accesoUbicacion.ubicacionParaConsultar(ubicacionId)),
                Especificaciones.igual("cliente.id", clienteId),
                Especificaciones.igual("estado", EstadoVenta.EMITIDA),
                (root, query, cb) -> cb.greaterThan(root.get("saldoPendiente"), BigDecimal.ZERO),
                soloVencidas ? Especificaciones.antesDe("fechaVencimiento", calendario.hoy())
                        : Specification.unrestricted());
        return PaginaResponse.de(ventaRepository.findAll(filtro,
                Ordenamiento.validar(pageable, "id", "fecha", "fechaVencimiento", "saldoPendiente", "total")), VentaResponse::resumen);
    }

    /** Clientes que deben, ordenados de mayor a menor deuda. */
    @Transactional(readOnly = true)
    public PaginaResponse<DeudorResponse> listarDeudores(Long ubicacionId, Pageable pageable) {
        Long ubicacion = accesoUbicacion.ubicacionParaConsultar(ubicacionId);
        Pageable sinOrden = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()); // el orden lo fija la consulta
        return PaginaResponse.de(ventaRepository.deudores(ubicacion, sinOrden));
    }
}
