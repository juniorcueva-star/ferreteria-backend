package com.ferreteria.dto.cotizaciones;

/**
 * @param url     enlace https://wa.me/51XXXXXXXXX?text=... listo para abrir
 * @param mensaje el mismo texto sin codificar (para mostrarlo o copiarlo)
 */
public record EnlaceWhatsappResponse(String url, String mensaje) {
}
