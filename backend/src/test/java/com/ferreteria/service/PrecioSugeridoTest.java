package com.ferreteria.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PrecioSugeridoTest {

    @Test
    @DisplayName("costo x (1 + ganancia/100) + adicional, redondeado a 2 decimales")
    void formula() {
        assertThat(PrecioSugerido.calcular(new BigDecimal("100.0000"), new BigDecimal("30"), new BigDecimal("0.50")))
                .isEqualByComparingTo("130.50");
        assertThat(PrecioSugerido.calcular(new BigDecimal("3.3333"), new BigDecimal("30"), null))
                .isEqualByComparingTo("4.33");
    }

    @Test
    @DisplayName("El redondeo es HALF_UP (2.345 -> 2.35), no el redondeo bancario")
    void redondeoHalfUp() {
        assertThat(PrecioSugerido.calcular(new BigDecimal("2.3450"), BigDecimal.ZERO, BigDecimal.ZERO))
                .isEqualByComparingTo("2.35");
        assertThat(PrecioSugerido.calcular(new BigDecimal("2.0000"), new BigDecimal("12.5"), null))
                .isEqualByComparingTo("2.25");
    }

    @Test
    @DisplayName("Sin costo de referencia no hay precio sugerido")
    void sinCosto() {
        assertThat(PrecioSugerido.calcular(null, new BigDecimal("30"), BigDecimal.ONE)).isNull();
    }
}
