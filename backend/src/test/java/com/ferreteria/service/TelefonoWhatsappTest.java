package com.ferreteria.service;

import com.ferreteria.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelefonoWhatsappTest {

    @Test
    @DisplayName("Acepta celulares peruanos con o sin 51, espacios, guiones o +")
    void aceptaFormatosComunes() {
        assertThat(CotizacionService.normalizarTelefono("987654321")).isEqualTo("51987654321");
        assertThat(CotizacionService.normalizarTelefono("+51 987-654-321")).isEqualTo("51987654321");
        assertThat(CotizacionService.normalizarTelefono("(51) 987 654 321")).isEqualTo("51987654321");
    }

    @Test
    @DisplayName("Rechaza telefonos fijos, cortos o con letras")
    void rechazaInvalidos() {
        for (String invalido : new String[]{"014567890", "98765432", "51887654321", "98765432a", ""}) {
            assertThatThrownBy(() -> CotizacionService.normalizarTelefono(invalido))
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }
}
