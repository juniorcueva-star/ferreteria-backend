package com.ferreteria.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Redondeos y calculos de dinero y cantidades, iguales a los de PostgreSQL (ROUND usa HALF_UP),
 * para que los CHECK de la base de datos siempre cuadren.
 */
public final class Montos {

    /** IGV de Peru: 18 %. */
    public static final BigDecimal TASA_IGV = new BigDecimal("0.18");

    private static final BigDecimal UNO_MAS_IGV = BigDecimal.ONE.add(TASA_IGV);

    private Montos() {
    }

    /** Dinero: 2 decimales. */
    public static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    /** Cantidades (unidades, kilos, metros): 3 decimales. */
    public static BigDecimal cantidad(BigDecimal valor) {
        return valor.setScale(3, RoundingMode.HALF_UP);
    }

    /** Costo unitario: 4 decimales. */
    public static BigDecimal costo(BigDecimal valor) {
        return valor.setScale(4, RoundingMode.HALF_UP);
    }

    /** Separa un total con IGV incluido en su base imponible (sin IGV). */
    public static BigDecimal baseSinIgv(BigDecimal totalConIgv) {
        return totalConIgv.divide(UNO_MAS_IGV, 2, RoundingMode.HALF_UP);
    }
}
