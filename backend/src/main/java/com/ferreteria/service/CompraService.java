package com.ferreteria.service;

import com.ferreteria.dto.comun.AnulacionRequest;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.compras.CompraDetalleRequest;
import com.ferreteria.dto.compras.CompraRequest;
import com.ferreteria.dto.compras.CompraResponse;
import com.ferreteria.entity.Compra;
import com.ferreteria.entity.CompraDetalle;
import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.Proveedor;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.enums.EstadoCompra;
import com.ferreteria.entity.enums.TipoComprobanteCompra;
import com.ferreteria.entity.enums.TipoMovimiento;
import com.ferreteria.exception.DuplicadoException;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.CompraRepository;
import com.ferreteria.repository.Especificaciones;
import com.ferreteria.repository.ProveedorRepository;
import com.ferreteria.security.AccesoUbicacionService;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Compras a proveedores. Registrar una compra suma stock (kardex COMPRA); anularla lo resta (ANULACION_COMPRA).
 */
@Service
@RequiredArgsConstructor
public class CompraService {

    private static final int MAX_OBSERVACION = 300;

    private final CompraRepository compraRepository;
    private final ProveedorRepository proveedorRepository;
    private final MovimientoStockService movimientoStock;
    private final AccesoUbicacionService accesoUbicacion;
    private final Buscador buscador;
    private final Calendario calendario;

    @Transactional(readOnly = true)
    public PaginaResponse<CompraResponse> listar(Long proveedorId, Long empresaId, Long ubicacionId,
                                                 EstadoCompra estado, LocalDate desde, LocalDate hasta,
                                                 Pageable pageable) {
        calendario.validarRango(desde, hasta);
        Specification<Compra> filtro = Specification.allOf(
                Especificaciones.igual("proveedor.id", proveedorId),
                Especificaciones.igual("empresa.id", empresaId),
                Especificaciones.igual("ubicacion.id", accesoUbicacion.ubicacionParaConsultar(ubicacionId)),
                Especificaciones.igual("estado", estado),
                Especificaciones.desde("fechaEmision", desde),
                Especificaciones.antesDe("fechaEmision", hasta == null ? null : hasta.plusDays(1)));
        return PaginaResponse.de(compraRepository.findAll(filtro, Ordenamiento.validar(pageable, "id", "fechaEmision", "total", "createdAt")), CompraResponse::resumen);
    }

    @Transactional(readOnly = true)
    public CompraResponse obtener(Long id) {
        Compra compra = buscar(id);
        accesoUbicacion.validarAcceso(compra.getUbicacion().getId());
        return CompraResponse.completa(compra);
    }

    @Transactional
    public CompraResponse registrar(CompraRequest request) {
        Long ubicacionId = accesoUbicacion.ubicacionParaOperar(request.ubicacionId());
        if (request.fechaEmision().isAfter(calendario.hoy())) {
            throw new ReglaNegocioException("La fecha de emision no puede ser futura");
        }
        String serieNumero = request.serieNumero() == null || request.serieNumero().isBlank()
                ? null : request.serieNumero().trim().toUpperCase();
        if (serieNumero != null && compraRepository.existsByProveedorIdAndSerieNumeroIgnoreCaseAndEstado(
                request.proveedorId(), serieNumero, EstadoCompra.REGISTRADA)) {
            throw new DuplicadoException("El comprobante " + serieNumero + " de ese proveedor ya fue registrado");
        }
        Usuario usuario = buscador.usuarioActual();

        Empresa empresa = buscador.empresaActiva(request.empresaId());
        Ubicacion ubicacion = buscador.ubicacionActiva(ubicacionId);
        // Una tienda solo recibe compras con su propio RUC; el almacen (compartido) recibe de ambas empresas
        if (ubicacion.getEmpresa() != null && !ubicacion.getEmpresa().getId().equals(empresa.getId())) {
            throw new ReglaNegocioException("La " + ubicacion.getNombre() + " pertenece a otra empresa: registre "
                    + "la compra con su RUC (" + ubicacion.getEmpresa().getRuc() + ")");
        }

        Compra compra = new Compra();
        compra.setEmpresa(empresa);
        compra.setProveedor(proveedorActivo(request.proveedorId()));
        compra.setUbicacion(ubicacion);
        compra.setUsuario(usuario);
        compra.setTipoComprobante(request.tipoComprobante());
        compra.setSerieNumero(serieNumero);
        compra.setFechaEmision(request.fechaEmision());
        compra.setObservacion(request.observacion());
        request.detalles().forEach(d -> compra.agregarDetalle(crearDetalle(d)));
        calcularTotales(compra);
        compraRepository.save(compra);

        List<LineaMovimiento> lineas = compra.getDetalles().stream()
                .map(d -> new LineaMovimiento(d.getProducto(), d.getCantidadBase(), d.getCostoUnitario()))
                .toList();
        movimientoStock.aplicar(compra.getUbicacion(), TipoMovimiento.COMPRA, lineas,
                OrigenMovimiento.compra(compra, usuario, null));
        return CompraResponse.completa(compra);
    }

    /**
     * Anula la compra y retira del stock lo que habia entrado. Si esa mercaderia ya se vendio o se traslado
     * (no queda stock suficiente) la anulacion se rechaza.
     */
    @Transactional
    public CompraResponse anular(Long id, AnulacionRequest request) {
        Compra compra = compraRepository.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Compra", id));
        accesoUbicacion.validarAcceso(compra.getUbicacion().getId());
        if (compra.getEstado() == EstadoCompra.ANULADA) {
            throw new ReglaNegocioException("La compra ya esta anulada");
        }
        Usuario usuario = buscador.usuarioActual();
        String motivo = request.motivo().trim();
        compra.setEstado(EstadoCompra.ANULADA);
        compra.setObservacion(agregarNota(compra.getObservacion(), "ANULADA: " + motivo));

        List<LineaMovimiento> lineas = compra.getDetalles().stream()
                .map(d -> new LineaMovimiento(d.getProducto(), d.getCantidadBase(), d.getCostoUnitario()))
                .toList();
        movimientoStock.aplicar(compra.getUbicacion(), TipoMovimiento.ANULACION_COMPRA, lineas,
                OrigenMovimiento.compra(compra, usuario, motivo));
        return CompraResponse.completa(compra);
    }

    /**
     * cantidad base = cantidad x factor; subtotal = cantidad x precio; costo por unidad base = precio / factor.
     */
    private CompraDetalle crearDetalle(CompraDetalleRequest request) {
        ProductoCantidad linea = buscador.lineaProducto(request.productoId(), request.presentacionId(),
                request.cantidad());
        BigDecimal factor = linea.presentacion() == null ? BigDecimal.ONE : linea.presentacion().getFactor();

        CompraDetalle detalle = new CompraDetalle();
        detalle.setProducto(linea.producto());
        detalle.setPresentacion(linea.presentacion());
        detalle.setCantidad(linea.cantidad());
        detalle.setCantidadBase(linea.cantidadBase());
        detalle.setSubtotal(Montos.dinero(request.cantidad().multiply(request.precioUnitario())));
        detalle.setCostoUnitario(request.precioUnitario().divide(factor, 4, RoundingMode.HALF_UP));
        return detalle;
    }

    /**
     * Los precios incluyen IGV. Con FACTURA se separa la base y el IGV (credito fiscal);
     * con otros comprobantes no hay IGV deducible y todo el monto queda como subtotal.
     */
    private static void calcularTotales(Compra compra) {
        BigDecimal total = compra.getDetalles().stream().map(CompraDetalle::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subtotal = compra.getTipoComprobante() == TipoComprobanteCompra.FACTURA
                ? Montos.baseSinIgv(total) : total;
        compra.setTotal(Montos.dinero(total));
        compra.setSubtotal(Montos.dinero(subtotal));
        compra.setIgv(Montos.dinero(total.subtract(subtotal)));
    }

    private Proveedor proveedorActivo(Long id) {
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proveedor", id));
        if (!proveedor.isActivo()) {
            throw new ReglaNegocioException("El proveedor " + proveedor.getRazonSocial() + " esta inactivo");
        }
        return proveedor;
    }

    private static String agregarNota(String observacion, String nota) {
        String texto = observacion == null || observacion.isBlank() ? nota : observacion + " | " + nota;
        return texto.length() <= MAX_OBSERVACION ? texto : texto.substring(texto.length() - MAX_OBSERVACION);
    }

    private Compra buscar(Long id) {
        return compraRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Compra", id));
    }
}
