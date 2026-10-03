package com.ferreteria.service;

import com.ferreteria.dto.comun.AnulacionRequest;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.inventario.LineaProductoRequest;
import com.ferreteria.dto.inventario.TrasladoRequest;
import com.ferreteria.dto.inventario.TrasladoResponse;
import com.ferreteria.entity.Traslado;
import com.ferreteria.entity.TrasladoDetalle;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.enums.EstadoTraslado;
import com.ferreteria.entity.enums.TipoMovimiento;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.Especificaciones;
import com.ferreteria.repository.TrasladoRepository;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.security.UsuarioActual;
import com.ferreteria.service.stock.LineaMovimiento;
import com.ferreteria.service.stock.MovimientoStockService;
import com.ferreteria.service.stock.OrigenMovimiento;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Traslados entre ubicaciones en dos pasos:
 * ENVIAR resta el stock del origen (TRASLADO_SALIDA) y RECIBIR lo suma en el destino (TRASLADO_ENTRADA).
 * Mientras esta ENVIADO la mercaderia "esta en camino": no figura en ninguna de las dos ubicaciones.
 */
@Service
@RequiredArgsConstructor
public class TrasladoService {

    private static final int MAX_OBSERVACION = 300;

    private final TrasladoRepository trasladoRepository;
    private final MovimientoStockService movimientoStock;
    private final AccesoUbicacionService accesoUbicacion;
    private final Buscador buscador;
    private final Calendario calendario;

    /**
     * El ADMIN puede filtrar por origen, destino o cualquier ubicacion (ubicacionId = origen o destino).
     * Los demas solo ven traslados que salen o llegan a su propia ubicacion.
     */
    @Transactional(readOnly = true)
    public PaginaResponse<TrasladoResponse> listar(Long ubicacionId, Long origenId, Long destinoId,
                                                   EstadoTraslado estado, LocalDate desde, LocalDate hasta,
                                                   Pageable pageable) {
        calendario.validarRango(desde, hasta);
        Long ubicacion = accesoUbicacion.ubicacionParaConsultar(ubicacionId);
        Specification<Traslado> filtro = Specification.allOf(
                Especificaciones.<Traslado>igual("origen.id", ubicacion)
                        .or(Especificaciones.igual("destino.id", ubicacion)),
                Especificaciones.igual("origen.id", origenId),
                Especificaciones.igual("destino.id", destinoId),
                Especificaciones.igual("estado", estado),
                Especificaciones.desde("fechaEnvio", calendario.inicio(desde)),
                Especificaciones.antesDe("fechaEnvio", calendario.finExclusivo(hasta)));
        return PaginaResponse.de(trasladoRepository.findAll(filtro, Ordenamiento.validar(pageable, "id", "codigo", "fechaEnvio", "estado")), TrasladoResponse::resumen);
    }

    @Transactional(readOnly = true)
    public TrasladoResponse obtener(Long id) {
        Traslado traslado = trasladoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Traslado", id));
        validarParticipa(traslado);
        return TrasladoResponse.completo(traslado);
    }

    @Transactional
    public TrasladoResponse enviar(TrasladoRequest request) {
        Long origenId = accesoUbicacion.ubicacionParaOperar(request.origenId());
        if (origenId.equals(request.destinoId())) {
            throw new ReglaNegocioException("El origen y el destino deben ser distintos");
        }
        Ubicacion origen = buscador.ubicacionActiva(origenId);
        Ubicacion destino = buscador.ubicacionActiva(request.destinoId());
        Usuario usuario = buscador.usuarioActual();

        Traslado traslado = new Traslado();
        traslado.setCodigo(String.format("TR-%06d", trasladoRepository.siguienteNumero()));
        traslado.setOrigen(origen);
        traslado.setDestino(destino);
        traslado.setUsuarioEnvia(usuario);
        traslado.setObservacion(request.observacion());
        for (LineaProductoRequest linea : request.detalles()) {
            ProductoCantidad pc = buscador.lineaProducto(linea.productoId(), linea.presentacionId(), linea.cantidad());
            TrasladoDetalle detalle = new TrasladoDetalle();
            detalle.setProducto(pc.producto());
            detalle.setPresentacion(pc.presentacion());
            detalle.setCantidad(pc.cantidad());
            detalle.setCantidadBase(pc.cantidadBase());
            traslado.agregarDetalle(detalle);
        }
        trasladoRepository.save(traslado);
        movimientoStock.aplicar(origen, TipoMovimiento.TRASLADO_SALIDA, lineas(traslado),
                OrigenMovimiento.traslado(traslado, usuario));
        return TrasladoResponse.completo(traslado);
    }

    /** Solo quien trabaja en la ubicacion destino (o el ADMIN) confirma que la mercaderia llego. */
    @Transactional
    public TrasladoResponse recibir(Long id) {
        Traslado traslado = bloquear(id);
        accesoUbicacion.validarAcceso(traslado.getDestino().getId());
        validarEnviado(traslado, "recibir");
        Usuario usuario = buscador.usuarioActual();
        traslado.setEstado(EstadoTraslado.RECIBIDO);
        traslado.setUsuarioRecibe(usuario);
        traslado.setFechaRecepcion(OffsetDateTime.now());
        movimientoStock.aplicar(traslado.getDestino(), TipoMovimiento.TRASLADO_ENTRADA, lineas(traslado),
                OrigenMovimiento.traslado(traslado, usuario));
        return TrasladoResponse.completo(traslado);
    }

    /** Solo un traslado ENVIADO (aun no recibido) se puede anular: el stock vuelve al origen. */
    @Transactional
    public TrasladoResponse anular(Long id, AnulacionRequest request) {
        Traslado traslado = bloquear(id);
        accesoUbicacion.validarAcceso(traslado.getOrigen().getId());
        validarEnviado(traslado, "anular");
        Usuario usuario = buscador.usuarioActual();
        traslado.setEstado(EstadoTraslado.ANULADO);
        String nota = "ANULADO: " + request.motivo().trim();
        String observacion = traslado.getObservacion() == null ? nota : traslado.getObservacion() + " | " + nota;
        traslado.setObservacion(observacion.length() <= MAX_OBSERVACION
                ? observacion : observacion.substring(observacion.length() - MAX_OBSERVACION));
        movimientoStock.aplicar(traslado.getOrigen(), TipoMovimiento.ANULACION_TRASLADO, lineas(traslado),
                OrigenMovimiento.traslado(traslado, usuario));
        return TrasladoResponse.completo(traslado);
    }

    private static List<LineaMovimiento> lineas(Traslado traslado) {
        return traslado.getDetalles().stream()
                .map(d -> LineaMovimiento.de(d.getProducto(), d.getCantidadBase()))
                .toList();
    }

    private static void validarEnviado(Traslado traslado, String accion) {
        if (traslado.getEstado() != EstadoTraslado.ENVIADO) {
            throw new ReglaNegocioException("No se puede " + accion + " el traslado " + traslado.getCodigo()
                    + " porque esta " + traslado.getEstado());
        }
    }

    private void validarParticipa(Traslado traslado) {
        UsuarioActual usuario = accesoUbicacion.usuarioActual();
        if (usuario.esAdmin()) {
            return;
        }
        if (!traslado.getOrigen().getId().equals(usuario.ubicacionId())) {
            accesoUbicacion.validarAcceso(traslado.getDestino().getId());
        }
    }

    private Traslado bloquear(Long id) {
        return trasladoRepository.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Traslado", id));
    }
}
