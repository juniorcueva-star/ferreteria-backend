package com.ferreteria.service.imagen;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formatos de imagen aceptados. Se reconocen por sus primeros bytes ("numero magico") y no por el
 * Content-Type que manda el cliente, que se puede falsificar.
 */
public enum TipoImagen {
    JPEG("jpg", new int[]{0xFF, 0xD8, 0xFF}),
    PNG("png", new int[]{0x89, 0x50, 0x4E, 0x47}),
    WEBP("webp", new int[]{0x52, 0x49, 0x46, 0x46}); // "RIFF" (se valida "WEBP" en los bytes 8 a 11)

    private final String extension;
    private final int[] firma;

    TipoImagen(String extension, int[] firma) {
        this.extension = extension;
        this.firma = firma;
    }

    public String getExtension() {
        return extension;
    }

    public static Optional<TipoImagen> detectar(byte[] contenido) {
        return Arrays.stream(values()).filter(tipo -> tipo.coincide(contenido)).findFirst();
    }

    private boolean coincide(byte[] contenido) {
        if (contenido.length < 12) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if ((contenido[i] & 0xFF) != firma[i]) {
                return false;
            }
        }
        return this != WEBP || (contenido[8] == 'W' && contenido[9] == 'E' && contenido[10] == 'B' && contenido[11] == 'P');
    }
}
