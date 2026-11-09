package com.ferreteria.service;

import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.exception.ReglaNegocioException;

import java.math.BigDecimal;

/**
 * Cuantos decimales acepta una cantidad segun la presentacion (Ej: una Docena no se vende en fracciones; un Kilo
 * acepta 0.250). Se aplica en ventas, compras, cotizaciones y encargos.
 */
public final class ReglaDecimales {

    private ReglaDecimales() {
    }

    /** Decimales por defecto segun la unidad base: enteros para UNIDAD; 3 para KILO y METRO. */
    public static int porUnidadBase(UnidadBase unidadBase) {
        return unidadBase == UnidadBase.UNIDAD ? 0 : 3;
    }

    /**
     * Rechaza la cantidad si tiene mas decimales de los que acepta la presentacion. Si no hay presentacion, la
     * cantidad esta en unidad base y se usa la regla de la unidad base del producto.
     */
    public static void validar(BigDecimal cantidad, Presentacion presentacion, Producto producto) {
        int permitidos = presentacion != null ? presentacion.getDecimales() : porUnidadBase(producto.getUnidadBase());
        if (cantidad.stripTrailingZeros().scale() <= permitidos) {
            return;
        }
        String nombre = presentacion != null ? presentacion.getNombre() : producto.getUnidadBase().name();
        throw new ReglaNegocioException(permitidos == 0
                ? "La presentacion " + nombre + " de " + producto.getNombre() + " no acepta fracciones"
                : "La presentacion " + nombre + " de " + producto.getNombre() + " acepta como maximo "
                        + permitidos + " decimales");
    }
}
