package com.ferreteria.service;

import com.ferreteria.dto.cotizaciones.EnlaceWhatsappResponse;
import com.ferreteria.exception.ReglaNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * Enlaces de WhatsApp (https://wa.me/51XXXXXXXXX?text=...) y enlaces publicos de los PDF. La URL publica sale de
 * APP_URL_PUBLICA (en la nube) o, si no esta, de la direccion con la que se llamo a la API.
 */
@Component
public class Whatsapp {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    @Value("${app.url-publica:}")
    private String urlPublica;

    /** Enlace que abre WhatsApp con el mensaje escrito para el celular indicado. */
    public EnlaceWhatsappResponse enlace(String telefono, String mensaje) {
        String codificado = URLEncoder.encode(mensaje, StandardCharsets.UTF_8).replace("+", "%20");
        return new EnlaceWhatsappResponse("https://wa.me/" + numero(telefono) + "?text=" + codificado, mensaje);
    }

    /** Direccion publica de la API (sin "/" al final), para los enlaces de descarga. */
    public String baseUrl() {
        if (urlPublica != null && !urlPublica.isBlank()) {
            return urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
    }

    /** Token aleatorio de 256 bits para los enlaces publicos (imposible de adivinar). */
    public static String token() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Celular peruano: 9 digitos que empiezan con 9 (con o sin 51 delante). */
    static String numero(String telefono) {
        String digitos = telefono == null ? "" : telefono.replaceAll("[\\s()+-]", "");
        if (digitos.matches("9\\d{8}")) {
            return "51" + digitos;
        }
        if (digitos.matches("519\\d{8}")) {
            return digitos;
        }
        throw new ReglaNegocioException("Celular invalido: 9 digitos que empiezan con 9 (Ej: 987654321)");
    }

    public static String soles(BigDecimal monto) {
        return String.format(Locale.US, "S/ %,.2f", monto);
    }
}
