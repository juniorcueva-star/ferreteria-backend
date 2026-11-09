package com.ferreteria.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusquedaTextoTest {

    @Test
    void normalizaSinAcentosNiMayusculas() {
        assertEquals("nandu", BusquedaTexto.normalizar("  Ñandú "));
        assertEquals("", BusquedaTexto.normalizar(null));
    }

    @Test
    void separaLasPalabrasDeLaConsulta() {
        assertEquals(List.of("per", "hex", "zin"), BusquedaTexto.tokens("  Per  HEX   zin "));
        assertEquals(List.of(), BusquedaTexto.tokens("   "));
    }

    @Test
    void indiceGuardaCadaParteNormalizadaYOmiteVacias() {
        assertEquals(" perno hexagonal 1/4 x 1/2 ", BusquedaTexto.indice("PERNO hexagonal", "1/4 X 1/2", null, " "));
    }

    @Test
    void abreviaturaTieneSuTerminoComoAlternativa() {
        Map<String, String> sinonimos = Map.of("hex", "hexagonal");
        assertEquals(List.of("hex", "hexagonal"), BusquedaTexto.alternativas("hex", sinonimos));
        assertEquals(List.of("3"), BusquedaTexto.alternativas("3", sinonimos));
    }

    @Test
    void patronBuscaAlInicioDeCualquierPalabra() {
        assertEquals("% per%", BusquedaTexto.patronInicioPalabra("per"));
        assertEquals("% 50\\%%", BusquedaTexto.patronInicioPalabra("50%"));
    }
}
