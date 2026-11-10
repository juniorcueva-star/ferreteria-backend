package com.ferreteria.service;

import com.ferreteria.entity.Completo;
import com.ferreteria.entity.CompletoComponente;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.Producto;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.CompletoRepository;
import com.ferreteria.repository.PresentacionRepository;
import com.ferreteria.service.stock.LineaMovimiento;
import com.ferreteria.util.Montos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resuelve las lineas de una venta o cotizacion: cada linea es de una presentacion (Ej: 2 docenas) o de un
 * completo (Ej: 100 pernos completos = 100 pernos + 100 tuercas + 100 arandelas). Valida que existan, que esten
 * activos y los decimales; y arma las salidas de stock (un completo descuenta cada una de sus piezas).
 */
@Component
@RequiredArgsConstructor
public class LineasDocumento {

    private static final int MAX_DESCRIPCION = 200;

    /** Lo que identifica una linea: su presentacion o su completo (solo uno de los dos). */
    public record Clave(Long presentacionId, Long completoId) {

        public static Clave presentacion(Long id) {
            return new Clave(id, null);
        }

        public static Clave completo(Long id) {
            return new Clave(null, id);
        }
    }

    public record Pedido(Long presentacionId, Long completoId, BigDecimal cantidad) {
    }

    /**
     * Linea validada. En un completo: presentacion null, factor 1 y cantidadBase = cantidad de completos.
     *
     * @param precioLista precio de la presentacion o del completo
     */
    public record Linea(Clave clave, Producto producto, Presentacion presentacion, Completo completo,
                        String descripcion, String composicion, BigDecimal cantidad, BigDecimal factor,
                        BigDecimal cantidadBase, BigDecimal precioLista) {
    }

    private final PresentacionRepository presentacionRepository;
    private final CompletoRepository completoRepository;

    public List<Linea> resolver(List<Pedido> pedidos) {
        for (Pedido p : pedidos) {
            if ((p.presentacionId() == null) == (p.completoId() == null)) {
                throw new ReglaNegocioException("Cada linea debe indicar una presentacion o un completo (solo uno)");
            }
        }
        Map<Long, Presentacion> presentaciones = presentacionRepository.findByIdIn(pedidos.stream()
                        .map(Pedido::presentacionId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Presentacion::getId, Function.identity()));
        Map<Long, Completo> completos = completoRepository.findAllById(pedidos.stream()
                        .map(Pedido::completoId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Completo::getId, Function.identity()));
        List<Linea> lineas = new ArrayList<>();
        for (Pedido p : pedidos) {
            lineas.add(p.presentacionId() != null
                    ? dePresentacion(presentaciones.get(p.presentacionId()), p)
                    : deCompleto(completos.get(p.completoId()), p));
        }
        return lineas;
    }

    /**
     * Salidas (o devoluciones) de stock de una linea: la presentacion descuenta su cantidad base; el completo,
     * cada pieza por la cantidad de completos.
     */
    public static List<LineaMovimiento> movimientos(Producto producto, Completo completo, BigDecimal cantidad,
                                                    BigDecimal cantidadBase) {
        if (completo == null) {
            return List.of(LineaMovimiento.de(producto, cantidadBase));
        }
        return completo.getComponentes().stream()
                .map(c -> LineaMovimiento.de(c.getProducto(), Montos.cantidad(cantidad.multiply(c.cantidadBasePorCompleto()))))
                .toList();
    }

    private static Linea dePresentacion(Presentacion presentacion, Pedido p) {
        if (presentacion == null) {
            throw new RecursoNoEncontradoException("Presentacion", p.presentacionId());
        }
        Producto producto = presentacion.getProducto();
        if (!presentacion.isActivo() || !producto.isActivo()) {
            throw new ReglaNegocioException(producto.getNombre() + " (" + presentacion.getNombre()
                    + ") no esta disponible");
        }
        ReglaDecimales.validar(p.cantidad(), presentacion, producto);
        BigDecimal cantidad = Montos.cantidad(p.cantidad());
        String descripcion = recortar(producto.getNombre() + " - " + presentacion.getNombre());
        BigDecimal cantidadBase = Montos.cantidad(cantidad.multiply(presentacion.getFactor()));
        if (cantidadBase.signum() <= 0) {
            throw new ReglaNegocioException("La cantidad de " + descripcion + " es demasiado pequena");
        }
        return new Linea(Clave.presentacion(presentacion.getId()), producto, presentacion, null, descripcion, null,
                cantidad, presentacion.getFactor(), cantidadBase, presentacion.getPrecioVenta());
    }

    private static Linea deCompleto(Completo completo, Pedido p) {
        if (completo == null) {
            throw new RecursoNoEncontradoException("Completo", p.completoId());
        }
        Producto producto = completo.getProducto();
        String descripcion = recortar(producto.getNombre() + " - " + completo.getNombre());
        if (!completo.isActivo() || !producto.isActivo()) {
            throw new ReglaNegocioException(descripcion + " no esta disponible");
        }
        for (CompletoComponente c : completo.getComponentes()) {
            if (!c.getProducto().isActivo() || !c.getPresentacion().isActivo()) {
                throw new ReglaNegocioException(descripcion + " no esta disponible: la pieza "
                        + c.getProducto().getNombre() + " esta inactiva");
            }
        }
        if (p.cantidad().stripTrailingZeros().scale() > 0) {
            throw new ReglaNegocioException(descripcion + " se vende por unidades enteras");
        }
        BigDecimal cantidad = Montos.cantidad(p.cantidad());
        return new Linea(Clave.completo(completo.getId()), producto, null, completo, descripcion,
                completo.composicion(), cantidad, BigDecimal.ONE.setScale(3), cantidad, completo.getPrecioVenta());
    }

    private static String recortar(String texto) {
        return texto.length() <= MAX_DESCRIPCION ? texto : texto.substring(0, MAX_DESCRIPCION);
    }
}
