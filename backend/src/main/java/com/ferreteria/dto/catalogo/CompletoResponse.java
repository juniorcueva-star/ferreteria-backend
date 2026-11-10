package com.ferreteria.dto.catalogo;

import com.ferreteria.entity.Completo;
import com.ferreteria.entity.CompletoComponente;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.util.Montos;

import java.math.BigDecimal;
import java.util.List;

/**
 * Completo con sus piezas.
 *
 * @param precioSugerido suma de las piezas a su precio de lista (referencia para fijar el precio del completo)
 * @param composicion    Ej: "1 Perno hexagonal (Unidad) + 1 Tuerca 3/8 (Unidad)"
 */
public record CompletoResponse(Long id, Long productoId, String nombre, BigDecimal precioVenta,
                               BigDecimal precioSugerido, boolean activo, String composicion,
                               List<Componente> componentes) {

    /**
     * @param cantidadBase lo que se descuenta del stock de la pieza por cada completo (cantidad x factor)
     */
    public record Componente(Long productoId, String productoCodigo, String productoNombre, UnidadBase unidadBase,
                             Long presentacionId, String presentacionNombre, BigDecimal cantidad,
                             BigDecimal cantidadBase, BigDecimal precioUnitario, boolean disponible) {

        static Componente desde(CompletoComponente c) {
            return new Componente(c.getProducto().getId(), c.getProducto().getCodigo(), c.getProducto().getNombre(),
                    c.getProducto().getUnidadBase(), c.getPresentacion().getId(), c.getPresentacion().getNombre(),
                    c.getCantidad(), Montos.cantidad(c.cantidadBasePorCompleto()), c.getPresentacion().getPrecioVenta(),
                    c.getProducto().isActivo() && c.getPresentacion().isActivo());
        }
    }

    public static CompletoResponse desde(Completo c) {
        return new CompletoResponse(c.getId(), c.getProducto().getId(), c.getNombre(), c.getPrecioVenta(),
                precioSugerido(c), c.isActivo(), c.composicion(),
                c.getComponentes().stream().map(Componente::desde).toList());
    }

    /** Suma de las piezas a precio de lista: ROUND(cantidad x precio, 2) por pieza. */
    public static BigDecimal precioSugerido(Completo c) {
        return c.getComponentes().stream()
                .map(p -> Montos.dinero(p.getCantidad().multiply(p.getPresentacion().getPrecioVenta())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
