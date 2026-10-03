package com.ferreteria.service.stock;

import com.ferreteria.entity.MovimientoInventario;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Stock;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.enums.TipoMovimiento;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.exception.StockInsuficienteException;
import com.ferreteria.repository.MovimientoInventarioRepository;
import com.ferreteria.repository.StockRepository;
import com.ferreteria.util.Montos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * UNICO punto del sistema que modifica el stock. Cada cambio:
 * <ol>
 *   <li>bloquea la fila de stock (SELECT ... FOR UPDATE) para que dos operaciones simultaneas no se pisen,</li>
 *   <li>valida que no quede negativo,</li>
 *   <li>actualiza la cantidad y</li>
 *   <li>registra la fila del kardex con el saldo resultante.</li>
 * </ol>
 * Exige una transaccion ya abierta (MANDATORY): el stock y el documento que lo mueve (compra, venta, traslado)
 * se guardan juntos o no se guarda nada.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MovimientoStockService {

    private final StockRepository stockRepository;
    private final MovimientoInventarioRepository movimientoRepository;

    /**
     * Aplica las lineas en la ubicacion. Si un producto aparece varias veces se suman. Las filas se bloquean
     * siempre en el mismo orden (por id de producto) para evitar bloqueos cruzados (deadlocks) entre transacciones.
     */
    public List<MovimientoInventario> aplicar(Ubicacion ubicacion, TipoMovimiento tipo, List<LineaMovimiento> lineas,
                                              OrigenMovimiento origen) {
        List<MovimientoInventario> movimientos = new ArrayList<>();
        for (LineaMovimiento linea : agruparPorProducto(lineas).values()) {
            movimientos.add(aplicarLinea(ubicacion, tipo, linea, origen));
        }
        return movimientos;
    }

    private MovimientoInventario aplicarLinea(Ubicacion ubicacion, TipoMovimiento tipo, LineaMovimiento linea,
                                              OrigenMovimiento origen) {
        Producto producto = linea.producto();
        BigDecimal cantidad = Montos.cantidad(linea.cantidadBase());
        validarCantidad(producto, cantidad);

        if (tipo.esEntrada()) {
            stockRepository.crearSiNoExiste(producto.getId(), ubicacion.getId());
        }
        Stock stock = stockRepository.bloquear(producto.getId(), ubicacion.getId()).orElse(null);
        BigDecimal disponible = stock == null ? BigDecimal.ZERO : stock.getCantidad();
        BigDecimal saldo = tipo.esEntrada() ? disponible.add(cantidad) : disponible.subtract(cantidad);
        if (stock == null || saldo.signum() < 0) {
            throw new StockInsuficienteException(producto.getNombre(), ubicacion.getNombre(), disponible, cantidad);
        }
        stock.setCantidad(saldo);

        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.setProducto(producto);
        movimiento.setUbicacion(ubicacion);
        movimiento.setTipo(tipo);
        movimiento.setCantidad(tipo.esEntrada() ? cantidad : cantidad.negate());
        movimiento.setSaldoResultante(saldo);
        movimiento.setCostoUnitario(linea.costoUnitario());
        movimiento.setCompra(origen.compra());
        movimiento.setVenta(origen.venta());
        movimiento.setTraslado(origen.traslado());
        movimiento.setUsuario(origen.usuario());
        movimiento.setMotivo(origen.motivo());
        return movimientoRepository.save(movimiento);
    }

    private static void validarCantidad(Producto producto, BigDecimal cantidad) {
        if (cantidad.signum() <= 0) {
            throw new ReglaNegocioException("La cantidad de " + producto.getNombre() + " debe ser mayor que 0");
        }
        if (producto.getUnidadBase() == UnidadBase.UNIDAD && cantidad.stripTrailingZeros().scale() > 0) {
            throw new ReglaNegocioException(producto.getNombre()
                    + " se cuenta por unidades: la cantidad total debe ser un numero entero (recibido "
                    + cantidad.stripTrailingZeros().toPlainString() + ")");
        }
    }

    private static Map<Long, LineaMovimiento> agruparPorProducto(List<LineaMovimiento> lineas) {
        Map<Long, LineaMovimiento> porProducto = new TreeMap<>(); // TreeMap = ordenado por id de producto
        for (LineaMovimiento linea : lineas) {
            porProducto.merge(linea.producto().getId(), linea, (a, b) -> new LineaMovimiento(a.producto(),
                    a.cantidadBase().add(b.cantidadBase()), costoPromedio(a, b)));
        }
        return porProducto;
    }

    /** Si un producto viene en dos lineas de compra con costos distintos, se guarda el costo promedio ponderado. */
    private static BigDecimal costoPromedio(LineaMovimiento a, LineaMovimiento b) {
        if (a.costoUnitario() == null || b.costoUnitario() == null) {
            return null;
        }
        BigDecimal total = a.costoUnitario().multiply(a.cantidadBase()).add(b.costoUnitario().multiply(b.cantidadBase()));
        return Montos.costo(total.divide(a.cantidadBase().add(b.cantidadBase()), 8, RoundingMode.HALF_UP));
    }
}
