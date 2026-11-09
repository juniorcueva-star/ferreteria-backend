package com.ferreteria.service;

import com.ferreteria.entity.Presentacion;
import com.ferreteria.util.Montos;

import java.math.BigDecimal;

/**
 * Precio sugerido de una presentacion (regla D10):
 * redondear_2(costo de referencia x (1 + ganancia / 100) + monto adicional), con HALF_UP.
 * Sin costo de referencia no hay sugerencia. Nunca cambia el precio final: solo informa.
 */
public final class PrecioSugerido {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private PrecioSugerido() {
    }

    public static BigDecimal calcular(BigDecimal costoReferencia, BigDecimal porcentajeGanancia,
                                      BigDecimal montoAdicional) {
        if (costoReferencia == null) {
            return null;
        }
        BigDecimal ganancia = porcentajeGanancia == null ? BigDecimal.ZERO : porcentajeGanancia;
        BigDecimal adicional = montoAdicional == null ? BigDecimal.ZERO : montoAdicional;
        BigDecimal factor = BigDecimal.ONE.add(ganancia.divide(CIEN));
        return Montos.dinero(costoReferencia.multiply(factor).add(adicional));
    }

    /** Recalcula y guarda el precio sugerido con los datos actuales de la presentacion. */
    public static void actualizar(Presentacion presentacion) {
        presentacion.setPrecioSugerido(calcular(presentacion.getCostoReferencia(),
                presentacion.getPorcentajeGanancia(), presentacion.getMontoAdicional()));
    }
}
