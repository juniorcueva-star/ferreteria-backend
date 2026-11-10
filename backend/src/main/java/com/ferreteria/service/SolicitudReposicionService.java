package com.ferreteria.service;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.inventario.LineaProductoRequest;
import com.ferreteria.dto.inventario.TrasladoRequest;
import com.ferreteria.dto.inventario.TrasladoResponse;
import com.ferreteria.dto.reposicion.AtencionSolicitudRequest;
import com.ferreteria.dto.reposicion.SolicitudReposicionRequest;
import com.ferreteria.dto.reposicion.SolicitudReposicionResponse;
import com.ferreteria.dto.reposicion.SugerenciaReposicionResponse;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.SolicitudReposicion;
import com.ferreteria.entity.SolicitudReposicionDetalle;
import com.ferreteria.entity.Stock;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.enums.EstadoSolicitud;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.entity.enums.TipoUbicacion;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.Especificaciones;
import com.ferreteria.repository.SolicitudReposicionRepository;
import com.ferreteria.repository.StockRepository;
import com.ferreteria.repository.TrasladoRepository;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.security.UsuarioActual;
import com.ferreteria.util.Montos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pedidos de reposicion: la tienda pide al almacen lo que se le acaba; el almacen lo atiende con un traslado
 * (puede enviar menos de lo pedido) o lo rechaza; la tienda puede anularlo mientras esta pendiente.
 * El VENDEDOR ve los pedidos de su tienda; el ALMACENERO y el ADMIN ven todos.
 */
@Service
@RequiredArgsConstructor
public class SolicitudReposicionService {

    private final SolicitudReposicionRepository solicitudRepository;
    private final StockRepository stockRepository;
    private final TrasladoRepository trasladoRepository;
    private final TrasladoService trasladoService;
    private final AccesoUbicacionService accesoUbicacion;
    private final Buscador buscador;

    @Transactional
    public SolicitudReposicionResponse crear(SolicitudReposicionRequest request) {
        Ubicacion tienda = buscador.ubicacionActiva(accesoUbicacion.ubicacionParaOperar(request.ubicacionId()));
        if (tienda.getTipo() != TipoUbicacion.TIENDA) {
            throw new ReglaNegocioException("Los pedidos de reposicion se hacen desde una tienda");
        }
        SolicitudReposicion solicitud = new SolicitudReposicion();
        solicitud.setCodigo(String.format("SR-%06d", solicitudRepository.siguienteNumero()));
        solicitud.setUbicacion(tienda);
        solicitud.setUsuario(buscador.usuarioActual());
        solicitud.setObservacion(request.observacion() == null || request.observacion().isBlank()
                ? null : request.observacion().trim());
        Set<Long> productos = new HashSet<>();
        for (SolicitudReposicionRequest.Linea linea : request.detalles()) {
            if (!productos.add(linea.productoId())) {
                throw new ReglaNegocioException("Un producto esta repetido: sume la cantidad en una sola linea");
            }
            Producto producto = buscador.productoActivo(linea.productoId());
            validarDecimales(producto, linea.cantidad());
            SolicitudReposicionDetalle detalle = new SolicitudReposicionDetalle();
            detalle.setProducto(producto);
            detalle.setCantidad(Montos.cantidad(linea.cantidad()));
            solicitud.agregarDetalle(detalle);
        }
        return SolicitudReposicionResponse.desde(solicitudRepository.save(solicitud));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<SolicitudReposicionResponse> listar(EstadoSolicitud estado, Long ubicacionId,
                                                              Pageable pageable) {
        Specification<SolicitudReposicion> filtro = Specification.allOf(
                Especificaciones.igual("estado", estado),
                Especificaciones.igual("ubicacion.id", tiendaParaConsultar(ubicacionId)));
        return PaginaResponse.de(solicitudRepository.findAll(filtro,
                Ordenamiento.validar(pageable, "id", "createdAt")), SolicitudReposicionResponse::desde);
    }

    @Transactional(readOnly = true)
    public SolicitudReposicionResponse obtener(Long id) {
        SolicitudReposicion solicitud = buscar(id);
        validarTienda(solicitud);
        return SolicitudReposicionResponse.desde(solicitud);
    }

    /**
     * Lo que conviene pedir para una tienda: productos que llegaron a su minimo, hasta el doble del minimo y sin
     * pasar de lo que hay en los almacenes.
     */
    @Transactional(readOnly = true)
    public List<SugerenciaReposicionResponse> sugerir(Long ubicacionId) {
        Long tienda = tiendaParaConsultar(ubicacionId);
        if (tienda == null) {
            throw new ReglaNegocioException("Indique la tienda (ubicacionId)");
        }
        List<Stock> enMinimo = stockRepository.enMinimo(tienda);
        Map<Long, BigDecimal> almacen = stockRepository.enAlmacenes(
                        enMinimo.stream().map(s -> s.getProducto().getId()).toList()).stream()
                .collect(Collectors.toMap(StockRepository.StockAlmacen::getProductoId,
                        StockRepository.StockAlmacen::getCantidad));
        return enMinimo.stream().map(s -> {
            Producto p = s.getProducto();
            BigDecimal enAlmacen = almacen.getOrDefault(p.getId(), BigDecimal.ZERO);
            BigDecimal falta = s.getStockMinimo().multiply(BigDecimal.TWO).subtract(s.getCantidad());
            if (ReglaDecimales.porUnidadBase(p.getUnidadBase()) == 0) {
                falta = falta.setScale(0, RoundingMode.CEILING);
            }
            BigDecimal sugerida = Montos.cantidad(falta.min(enAlmacen).max(BigDecimal.ZERO));
            return new SugerenciaReposicionResponse(p.getId(), p.getCodigo(), p.getNombre(), p.getUnidadBase(),
                    s.getCantidad(), s.getStockMinimo(), enAlmacen, sugerida);
        }).toList();
    }

    /** Crea el traslado del almacen a la tienda con lo que se envia y deja el pedido ATENDIDO. */
    @Transactional
    public SolicitudReposicionResponse atender(Long id, AtencionSolicitudRequest request) {
        SolicitudReposicion solicitud = bloquearPendiente(id);
        Map<Long, BigDecimal> enviar = solicitud.getDetalles().stream().collect(Collectors.toMap(
                d -> d.getProducto().getId(), SolicitudReposicionDetalle::getCantidad));
        if (request.detalles() != null && !request.detalles().isEmpty()) {
            enviar.clear();
            for (AtencionSolicitudRequest.Linea linea : request.detalles()) {
                if (solicitud.getDetalles().stream().noneMatch(d -> d.getProducto().getId().equals(linea.productoId()))) {
                    throw new ReglaNegocioException("El producto " + linea.productoId() + " no esta en el pedido");
                }
                enviar.put(linea.productoId(), linea.cantidad());
            }
        }
        List<LineaProductoRequest> lineas = enviar.entrySet().stream()
                .filter(e -> e.getValue().signum() > 0)
                .map(e -> new LineaProductoRequest(e.getKey(), null, e.getValue()))
                .toList();
        if (lineas.isEmpty()) {
            throw new ReglaNegocioException("Indique al menos un producto con cantidad mayor que 0 o rechace el pedido");
        }
        String observacion = "Atiende el pedido " + solicitud.getCodigo()
                + (request.observacion() == null || request.observacion().isBlank() ? "" : " · " + request.observacion().trim());
        TrasladoResponse traslado = trasladoService.enviar(new TrasladoRequest(request.origenId(),
                solicitud.getUbicacion().getId(), observacion.length() <= 300 ? observacion : observacion.substring(0, 300),
                lineas));
        solicitud.setTraslado(trasladoRepository.getReferenceById(traslado.id()));
        solicitud.setEstado(EstadoSolicitud.ATENDIDA);
        solicitud.setAtendidaPor(buscador.usuarioActual());
        solicitud.setAtendidaAt(OffsetDateTime.now());
        solicitudRepository.flush();
        return SolicitudReposicionResponse.desde(solicitud);
    }

    @Transactional
    public SolicitudReposicionResponse rechazar(Long id, String motivo) {
        SolicitudReposicion solicitud = bloquearPendiente(id);
        solicitud.setEstado(EstadoSolicitud.RECHAZADA);
        solicitud.setMotivo(motivo.trim());
        solicitud.setAtendidaPor(buscador.usuarioActual());
        solicitud.setAtendidaAt(OffsetDateTime.now());
        return SolicitudReposicionResponse.desde(solicitud);
    }

    /** La tienda que pidio (o el ADMIN) anula el pedido mientras sigue pendiente. */
    @Transactional
    public SolicitudReposicionResponse anular(Long id, String motivo) {
        SolicitudReposicion solicitud = bloquearPendiente(id);
        validarTienda(solicitud);
        solicitud.setEstado(EstadoSolicitud.ANULADA);
        solicitud.setMotivo(motivo.trim());
        return SolicitudReposicionResponse.desde(solicitud);
    }

    /** VENDEDOR: su tienda (no puede ver otra). ALMACENERO y ADMIN: la indicada o todas. */
    private Long tiendaParaConsultar(Long ubicacionId) {
        UsuarioActual usuario = accesoUbicacion.usuarioActual();
        if (usuario.rol() == Rol.VENDEDOR) {
            return accesoUbicacion.ubicacionParaConsultar(ubicacionId);
        }
        return ubicacionId;
    }

    private void validarTienda(SolicitudReposicion solicitud) {
        if (accesoUbicacion.usuarioActual().rol() == Rol.VENDEDOR) {
            accesoUbicacion.validarAcceso(solicitud.getUbicacion().getId());
        }
    }

    private SolicitudReposicion bloquearPendiente(Long id) {
        SolicitudReposicion solicitud = solicitudRepository.bloquear(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido de reposicion", id));
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new ReglaNegocioException("El pedido " + solicitud.getCodigo() + " ya esta "
                    + solicitud.getEstado().name().toLowerCase());
        }
        return solicitud;
    }

    private SolicitudReposicion buscar(Long id) {
        return solicitudRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido de reposicion", id));
    }

    private static void validarDecimales(Producto producto, BigDecimal cantidad) {
        int decimales = ReglaDecimales.porUnidadBase(producto.getUnidadBase());
        if (cantidad.stripTrailingZeros().scale() > decimales) {
            throw new ReglaNegocioException(producto.getNombre() + (decimales == 0
                    ? " se pide en unidades enteras" : " acepta hasta " + decimales + " decimales"));
        }
    }
}
