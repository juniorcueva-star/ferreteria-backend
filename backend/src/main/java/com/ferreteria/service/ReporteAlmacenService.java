package com.ferreteria.service;

import com.ferreteria.dto.reportes.PedidoSugeridoResponse;
import com.ferreteria.dto.reportes.ValorInventarioResponse;
import com.ferreteria.entity.CompraDetalle;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Stock;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.CompraDetalleRepository;
import com.ferreteria.repository.MovimientoInventarioRepository;
import com.ferreteria.repository.StockRepository;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.util.Montos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reportes del almacen: que comprar (pedido sugerido al proveedor) y cuanto dinero hay en stock (valor del
 * inventario) con los productos que no salen. Respetan la seguridad por ubicacion.
 */
@Service
@RequiredArgsConstructor
public class ReporteAlmacenService {

    private final StockRepository stockRepository;
    private final CompraDetalleRepository compraDetalleRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final AccesoUbicacionService accesoUbicacion;

    /**
     * Productos de una ubicacion (normalmente el almacen) que llegaron a su stock minimo, con cuanto comprar (hasta el
     * doble del minimo), el ultimo proveedor y el ultimo costo.
     */
    @Transactional(readOnly = true)
    public List<PedidoSugeridoResponse> pedidoSugerido(Long ubicacionId) {
        Long ubicacion = accesoUbicacion.ubicacionParaConsultar(ubicacionId);
        if (ubicacion == null) {
            throw new ReglaNegocioException("Indique la ubicacion (ubicacionId), normalmente el almacen");
        }
        List<Stock> enMinimo = stockRepository.enMinimo(ubicacion);
        Map<Long, CompraDetalle> ultimas = ultimasCompras(enMinimo.stream().map(s -> s.getProducto().getId()).toList());
        return enMinimo.stream().map(s -> {
            Producto p = s.getProducto();
            BigDecimal falta = s.getStockMinimo().multiply(BigDecimal.TWO).subtract(s.getCantidad());
            if (ReglaDecimales.porUnidadBase(p.getUnidadBase()) == 0) {
                falta = falta.setScale(0, RoundingMode.CEILING);
            }
            BigDecimal sugerida = Montos.cantidad(falta.max(BigDecimal.ZERO));
            CompraDetalle ultima = ultimas.get(p.getId());
            BigDecimal costo = ultima == null ? null : ultima.getCostoUnitario();
            return new PedidoSugeridoResponse(p.getId(), p.getCodigo(), p.getNombre(), p.getUnidadBase(),
                    s.getCantidad(), s.getStockMinimo(), sugerida,
                    ultima == null ? null : ultima.getCompra().getProveedor().getRazonSocial(),
                    ultima == null ? null : ultima.getCompra().getFechaEmision(), costo,
                    costo == null ? null : Montos.dinero(sugerida.multiply(costo)));
        }).toList();
    }

    /**
     * Valor del inventario (cantidad x costo de la ultima compra) por producto y ubicacion, y dias desde su ultima
     * salida. Con diasSinSalida solo devuelve los que no salen hace al menos esos dias (o nunca salieron).
     */
    @Transactional(readOnly = true)
    public ValorInventarioResponse valorInventario(Long ubicacionId, Integer diasSinSalida) {
        Long ubicacion = accesoUbicacion.ubicacionParaConsultar(ubicacionId);
        List<Stock> stocks = stockRepository.conCantidad(ubicacion);
        Map<Long, CompraDetalle> ultimas = ultimasCompras(stocks.stream().map(s -> s.getProducto().getId())
                .distinct().toList());
        Map<String, OffsetDateTime> salidas = movimientoRepository.ultimasSalidas(ubicacion).stream()
                .collect(Collectors.toMap(u -> u.getProductoId() + "-" + u.getUbicacionId(),
                        MovimientoInventarioRepository.UltimaSalida::getFecha));
        OffsetDateTime ahora = OffsetDateTime.now();
        List<ValorInventarioResponse.Fila> filas = stocks.stream().map(s -> {
            CompraDetalle ultima = ultimas.get(s.getProducto().getId());
            BigDecimal costo = ultima == null ? null : ultima.getCostoUnitario();
            OffsetDateTime salida = salidas.get(s.getProducto().getId() + "-" + s.getUbicacion().getId());
            Long dias = salida == null ? null : Duration.between(salida, ahora).toDays();
            return new ValorInventarioResponse.Fila(s.getProducto().getId(), s.getProducto().getCodigo(),
                    s.getProducto().getNombre(), s.getProducto().getUnidadBase(), s.getUbicacion().getId(),
                    s.getUbicacion().getNombre(), s.getCantidad(), costo,
                    costo == null ? null : Montos.dinero(s.getCantidad().multiply(costo)), salida, dias);
        }).filter(f -> diasSinSalida == null || f.diasSinSalida() == null || f.diasSinSalida() >= diasSinSalida)
                .toList();
        BigDecimal total = filas.stream().map(ValorInventarioResponse.Fila::valor).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long sinCosto = filas.stream().filter(f -> f.costoUnitario() == null).count();
        return new ValorInventarioResponse(total, filas.size(), sinCosto, filas);
    }

    /** Ultima compra registrada de cada producto. */
    private Map<Long, CompraDetalle> ultimasCompras(List<Long> productoIds) {
        Map<Long, CompraDetalle> ultimas = new HashMap<>();
        if (productoIds.isEmpty()) {
            return ultimas;
        }
        for (CompraDetalle d : compraDetalleRepository.comprasRecientes(productoIds)) {
            ultimas.putIfAbsent(d.getProducto().getId(), d);
        }
        return ultimas;
    }
}
