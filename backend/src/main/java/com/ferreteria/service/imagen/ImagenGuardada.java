package com.ferreteria.service.imagen;

/**
 * @param url      direccion publica de la imagen
 * @param publicId identificador para borrarla o reemplazarla
 */
public record ImagenGuardada(String url, String publicId) {
}
