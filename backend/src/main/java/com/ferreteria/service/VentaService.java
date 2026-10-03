package com.ferreteria.service;

import com.ferreteria.dto.comun.AnulacionRequest;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.ventas.VentaDetalleRequest;
import com.ferreteria.dto.ventas.VentaRequest;
import com.ferreteria.dto.ventas.VentaResponse;
import com.ferreteria.entity.CajaSesion;
import com.ferreteria.entity.Cliente;
import com.ferreteria.entity.Pago;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.SerieCorrelativo;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.Venta;
import com.ferreteria.entity.VentaDetalle;
import com.ferreteria.entity.enums.CondicionVenta;
import com.ferreteria.entity.enums.EstadoCaja;
import com.ferreteria.entity.enums.EstadoPago;
import com.ferreteria.entity.enums.EstadoVenta;
import com.ferreteria.entity.enums.TipoDocumentoVenta;
import com.ferreteria.entity.enums.TipoMovimiento;
import com.ferreteria.entity.enums.TipoPago;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.CajaSesionRepository;
import com.ferreteria.repository.ClienteRepository;
import com.ferreteria.repository.Especificaciones;
import com.ferreteria.repository.PresentacionRepository;
import com.ferreteria.repository.SerieCorrelativoRepository;
import com.ferreteria.repository.VentaRepository;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.security.UsuarioActual;
import com.ferreteria.service.stock.LineaMovimiento;
import com.ferreteria.service.stock.MovimientoStockService;
import com.ferreteria.service.stock.OrigenMovimiento;
import com.ferreteria.util.Montos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ventas al contado y a credito (fiado) con pago mixto. Todo ocurre en una sola transaccion:
 * numeracion, descuento de stock con bloqueo pesimista, kardex y pagos. Si algo falla no queda nada a medias.
 */
@Service
@RequiredArgsConstructor
public class VentaService {

    private static final TipoDocumentoVenta DOCUMENTO_FASE_1 = TipoDocumentoVenta.NOTA_VENTA;
    private static final int MAX_DESCRIPCION = 200;

    private final VentaRepository ventaRepository;
    private final PresentacionRepository presentacionRepository;
    private final ClienteRepository clienteRepository;
    private final SerieCorrelativoRepository serieRepository;
    private final CajaSesionRepository cajaRepository;
    private final MovimientoStockService movimientoStock;
    private final AccesoUbicacionService accesoUbicacion;
    private final Buscador buscador;
    private final Cobros cobros;
    private final Calendario calendario;

    @Transactional
    public VentaResponse registrar(VentaRequest request) {
        CajaSesion caja = cobros.cajaAbiertaDelUsuario();
        Ubicacion tienda = caja.getUbicacion();
        if (!tienda.isActivo()) {
            throw new ReglaNegocioException("La tienda " + tienda.getNombre() + " esta inactiva");
        }
        Usuario usuario = buscador.usuarioActual();

        Venta venta = new Venta();
        venta.setUbicacion(tienda);
        venta.setEmpresa(tienda.getEmpresa());
        venta.setUsuario(usuario);
        venta.setCajaSesion(caja);
        venta.setTipoDocumento(DOCUMENTO_FASE_1);
        venta.setCondicion(request.condicion());
        venta.setCliente(resolverCliente(request));
        venta.setFechaVencimiento(resolverVencimiento(request));
        agregarDetalles(venta, request.detalles());
        calcularTotales(venta);

        List<Pago> pagos = cobros.crearPagos(request.pagos() == null ? List.of() : request.pagos(), TipoPago.VENTA,
                caja, usuario);
        BigDecimal vuelto = aplicarPagos(venta, pagos);

        // Orden de bloqueos: caja (compartido) -> serie de la tienda -> filas de stock (por id de producto)
        asignarNumero(venta);
        ventaRepository.save(venta);
        List<LineaMovimiento> lineas = venta.getDetalles().stream()
                .map(d -> LineaMovimiento.de(d.getProducto(), d.getCantidadBase()))
                .toList();
        movimientoStock.aplicar(tienda, TipoMovimiento.VENTA, lineas,
                OrigenMovimiento.venta(venta, usuario, null));
        return VentaResponse.completa(venta, vuelto);
    }

    @Transactional(readOnly = true)
    public VentaResponse obtener(Long id) {
        Venta venta = ventaRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Venta", id));
        accesoUbicacion.validarAcceso(venta.getUbicacion().getId());
        return VentaResponse.completa(venta);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<VentaResponse> listar(Long ubicacionId, Long usuarioId, Long clienteId, Long cajaId,
                                                CondicionVenta condicion, EstadoVenta estado, Boolean conSaldo,
                                                LocalDate desde, LocalDate hasta, Pageable pageable) {
        calendario.validarRango(desde, hasta);
        Specification<Venta> filtro = Specification.allOf(
                Especificaciones.igual("ubicacion.id", accesoUbicacion.ubicacionParaConsultar(ubicacionId)),
                Especificaciones.igual("usuario.id", usuarioId),
                Especificaciones.igual("cliente.id", clienteId),
                Especificaciones.igual("cajaSesion.id", cajaId),
                Especificaciones.igual("condicion", condicion),
                Especificaciones.igual("estado", estado),
                conSaldo == null ? Specification.unrestricted() : conSaldo(conSaldo),
                Especificaciones.desde("fecha", calendario.inicio(desde)),
                Especificaciones.antesDe("fecha", calendario.finExclusivo(hasta)));
        return PaginaResponse.de(ventaRepository.findAll(filtro, pageable), VentaResponse::resumen);
    }

    /**
     * Anula la venta: devuelve el stock (kardex ANULACION_VENTA), anula sus pagos (salen del cuadre de su caja)
     * y deja el saldo en 0. Solo si todas las cajas donde se cobro siguen abiertas, porque el dinero se devuelve
     * desde esas cajas. El vendedor solo puede anular ventas de su tienda emitidas en una caja aun abierta.
     */
    @Transactional
    public VentaResponse anular(Long id, AnulacionRequest request) {
        Venta venta = ventaRepository.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Venta", id));
        UsuarioActual actual = accesoUbicacion.usuarioActual();
        accesoUbicacion.validarAcceso(venta.getUbicacion().getId());
        if (venta.getEstado() == EstadoVenta.ANULADA) {
            throw new ReglaNegocioException("La venta " + venta.getNumeroDocumento() + " ya esta anulada");
        }
        if (!actual.esAdmin() && venta.getCajaSesion().getEstado() != EstadoCaja.ABIERTA) {
            throw new ReglaNegocioException("Solo el ADMIN puede anular ventas de una caja ya cerrada");
        }
        List<Pago> pagosValidos = venta.getPagos().stream().filter(p -> p.getEstado() == EstadoPago.VALIDO).toList();
        for (Pago pago : pagosValidos) {
            CajaSesion cajaDelPago = cajaRepository.bloquearCompartido(pago.getCajaSesion().getId()).orElseThrow();
            if (cajaDelPago.getEstado() != EstadoCaja.ABIERTA) {
                throw new ReglaNegocioException("La venta tiene pagos en la caja " + cajaDelPago.getId()
                        + " que ya esta cerrada: no se puede devolver ese dinero desde el sistema");
            }
        }
        pagosValidos.forEach(p -> p.setEstado(EstadoPago.ANULADO));
        venta.setEstado(EstadoVenta.ANULADA);
        venta.setSaldoPendiente(BigDecimal.ZERO);
        venta.setMotivoAnulacion(request.motivo().trim());

        List<LineaMovimiento> lineas = venta.getDetalles().stream()
                .map(d -> LineaMovimiento.de(d.getProducto(), d.getCantidadBase()))
                .toList();
        movimientoStock.aplicar(venta.getUbicacion(), TipoMovimiento.ANULACION_VENTA, lineas,
                OrigenMovimiento.venta(venta, buscador.usuarioActual(), venta.getMotivoAnulacion()));
        return VentaResponse.completa(venta);
    }

    // ---------- armado de la venta ----------

    private Cliente resolverCliente(VentaRequest request) {
        if (request.clienteId() == null) {
            if (request.condicion() == CondicionVenta.CREDITO) {
                throw new ReglaNegocioException("Una venta al credito necesita un cliente");
            }
            return null;
        }
        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", request.clienteId()));
        if (!cliente.isActivo()) {
            throw new ReglaNegocioException("El cliente " + cliente.getNombre() + " esta inactivo");
        }
        return cliente;
    }

    private LocalDate resolverVencimiento(VentaRequest request) {
        if (request.fechaVencimiento() == null) {
            return null;
        }
        if (request.condicion() != CondicionVenta.CREDITO) {
            throw new ReglaNegocioException("La fecha de vencimiento solo aplica a ventas al credito");
        }
        if (request.fechaVencimiento().isBefore(calendario.hoy())) {
            throw new ReglaNegocioException("La fecha de vencimiento no puede ser anterior a hoy");
        }
        return request.fechaVencimiento();
    }

    private void agregarDetalles(Venta venta, List<VentaDetalleRequest> detalles) {
        Map<Long, Presentacion> presentaciones = presentacionRepository.findByIdIn(
                        detalles.stream().map(VentaDetalleRequest::presentacionId).toList())
                .stream().collect(Collectors.toMap(Presentacion::getId, Function.identity()));
        for (VentaDetalleRequest d : detalles) {
            Presentacion presentacion = presentaciones.get(d.presentacionId());
            if (presentacion == null) {
                throw new RecursoNoEncontradoException("Presentacion", d.presentacionId());
            }
            if (!presentacion.isActivo() || !presentacion.getProducto().isActivo()) {
                throw new ReglaNegocioException(presentacion.getProducto().getNombre() + " ("
                        + presentacion.getNombre() + ") no esta disponible para la venta");
            }
            BigDecimal cantidad = Montos.cantidad(d.cantidad());
            BigDecimal importe = Montos.dinero(cantidad.multiply(presentacion.getPrecioVenta()));
            BigDecimal descuento = Montos.dinero(d.descuento() == null ? BigDecimal.ZERO : d.descuento());
            if (descuento.compareTo(importe) > 0) {
                throw new ReglaNegocioException("El descuento de " + presentacion.getProducto().getNombre()
                        + " supera el importe de la linea (" + importe.toPlainString() + ")");
            }
            VentaDetalle detalle = new VentaDetalle();
            detalle.setProducto(presentacion.getProducto());
            detalle.setPresentacion(presentacion);
            detalle.setDescripcion(descripcion(presentacion));
            detalle.setCantidad(cantidad);
            detalle.setFactor(presentacion.getFactor());
            // Igual que el CHECK ck_venta_det_base: ROUND(cantidad * factor, 3)
            detalle.setCantidadBase(Montos.cantidad(cantidad.multiply(presentacion.getFactor())));
            detalle.setPrecioUnitario(presentacion.getPrecioVenta());
            detalle.setDescuento(descuento);
            detalle.setSubtotal(importe.subtract(descuento));
            if (detalle.getCantidadBase().signum() <= 0) {
                throw new ReglaNegocioException("La cantidad de " + detalle.getDescripcion() + " es demasiado pequena");
            }
            venta.agregarDetalle(detalle);
        }
    }

    /** Los precios incluyen IGV: total = suma de lineas; base = total / 1.18; IGV = total - base. */
    private static void calcularTotales(Venta venta) {
        BigDecimal total = venta.getDetalles().stream().map(VentaDetalle::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw new ReglaNegocioException("El total de la venta debe ser mayor que 0");
        }
        BigDecimal subtotal = Montos.baseSinIgv(total);
        venta.setTotal(Montos.dinero(total));
        venta.setSubtotal(subtotal);
        venta.setIgv(Montos.dinero(total.subtract(subtotal)));
        venta.setDescuento(Montos.dinero(venta.getDetalles().stream().map(VentaDetalle::getDescuento)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    /**
     * CONTADO: los pagos deben cubrir el total; lo pagado de mas solo se acepta en efectivo y es el vuelto
     * (se descuenta del pago en efectivo, porque ese dinero no se queda en la caja).
     * CREDITO: los pagos son un adelanto que no puede superar el total; el resto es el saldo pendiente.
     *
     * @return vuelto a entregar al cliente
     */
    private static BigDecimal aplicarPagos(Venta venta, List<Pago> pagos) {
        BigDecimal pagado = pagos.stream().map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = venta.getTotal();
        BigDecimal vuelto = BigDecimal.ZERO;
        List<Pago> pagosFinales = pagos;
        if (venta.getCondicion() == CondicionVenta.CONTADO) {
            if (pagado.compareTo(total) < 0) {
                throw new ReglaNegocioException("Los pagos (" + pagado.toPlainString() + ") no cubren el total ("
                        + total.toPlainString() + "). Para dejar saldo use condicion CREDITO");
            }
            vuelto = pagado.subtract(total);
            pagosFinales = descontarVuelto(pagos, vuelto);
            venta.setSaldoPendiente(BigDecimal.ZERO);
        } else {
            if (pagado.compareTo(total) > 0) {
                throw new ReglaNegocioException("El adelanto (" + pagado.toPlainString()
                        + ") no puede superar el total (" + total.toPlainString() + ")");
            }
            venta.setSaldoPendiente(total.subtract(pagado));
        }
        pagosFinales.forEach(venta::agregarPago);
        return vuelto;
    }

    private static List<Pago> descontarVuelto(List<Pago> pagos, BigDecimal vuelto) {
        if (vuelto.signum() == 0) {
            return pagos;
        }
        BigDecimal efectivo = pagos.stream().filter(p -> p.getMetodoPago().isEfectivo()).map(Pago::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (efectivo.compareTo(vuelto) < 0) {
            throw new ReglaNegocioException("Se pago de mas (" + vuelto.toPlainString()
                    + ") con metodos que no son efectivo: solo se puede dar vuelto del efectivo recibido");
        }
        BigDecimal porDescontar = vuelto;
        for (Pago pago : pagos.stream().filter(p -> p.getMetodoPago().isEfectivo())
                .sorted(Comparator.comparing(Pago::getMonto)).toList()) {
            BigDecimal descuento = porDescontar.min(pago.getMonto());
            pago.setMonto(pago.getMonto().subtract(descuento));
            porDescontar = porDescontar.subtract(descuento);
        }
        return pagos.stream().filter(p -> p.getMonto().signum() > 0).toList();
    }

    /** Toma el siguiente numero de la serie de la tienda (creandola la primera vez) con bloqueo exclusivo. */
    private void asignarNumero(Venta venta) {
        Long tiendaId = venta.getUbicacion().getId();
        if (!serieRepository.existsByUbicacionIdAndTipoDocumento(tiendaId, venta.getTipoDocumento())) {
            serieRepository.crearSiNoExiste(tiendaId, venta.getTipoDocumento().name(), serieInicial(tiendaId));
        }
        SerieCorrelativo serie = serieRepository
                .findFirstByUbicacionIdAndTipoDocumentoOrderByIdAsc(tiendaId, venta.getTipoDocumento())
                .orElseThrow();
        serie.setUltimoNumero(serie.getUltimoNumero() + 1);
        venta.setSerie(serie.getSerie());
        venta.setNumero(serie.getUltimoNumero());
    }

    /** NV + id de la tienda con 2 digitos (NV01, NV02...); maximo 4 caracteres como exige la columna. */
    private static String serieInicial(Long tiendaId) {
        return tiendaId < 100 ? String.format("NV%02d", tiendaId) : String.format("N%03d", tiendaId % 1000);
    }

    private static String descripcion(Presentacion presentacion) {
        String texto = presentacion.getProducto().getNombre() + " - " + presentacion.getNombre();
        return texto.length() <= MAX_DESCRIPCION ? texto : texto.substring(0, MAX_DESCRIPCION);
    }

    private static Specification<Venta> conSaldo(boolean conSaldo) {
        return (root, query, cb) -> conSaldo
                ? cb.greaterThan(root.get("saldoPendiente"), BigDecimal.ZERO)
                : cb.equal(root.get("saldoPendiente"), BigDecimal.ZERO);
    }
}
