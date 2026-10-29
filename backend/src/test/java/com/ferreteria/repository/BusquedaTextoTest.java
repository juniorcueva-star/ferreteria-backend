package com.ferreteria.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusquedaTextoTest {

    @Test
    void separaAbreviaturasYQuitaTildes() {
        assertThat(BusquedaTexto.tokens(" Pe Gal ")).containsExactly("pe", "gal");
        assertThat(BusquedaTexto.tokens("ARÁN PÉS")).containsExactly("aran", "pes");
    }

    @Test
    void ignoraBusquedaVacia() {
        assertThat(BusquedaTexto.tokens("   ")).isEmpty();
    }
}
