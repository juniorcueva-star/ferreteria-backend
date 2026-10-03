package com.ferreteria.service.imagen;

import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Guarda las fotos en una carpeta del servidor y las publica en /imagenes/{archivo}.
 * Se usa cuando no hay CLOUDINARY_URL: el sistema funciona sin cuentas externas.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${app.imagenes.cloudinary-url:}'.isBlank()")
public class AlmacenImagenesLocal implements AlmacenImagenes {

    static final String PREFIJO = "local/";

    private final Path directorio;

    public AlmacenImagenesLocal(@Value("${app.imagenes.directorio}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }

    @Override
    public ImagenGuardada guardar(byte[] contenido, TipoImagen tipo) {
        // El nombre lo genera el sistema (UUID): el cliente no puede elegir rutas
        String archivo = UUID.randomUUID() + "." + tipo.getExtension();
        try {
            Files.createDirectories(directorio);
            Files.write(directorio.resolve(archivo), contenido);
        } catch (IOException e) {
            throw new ApiException(CodigoError.ERROR_INTERNO, "No se pudo guardar la imagen");
        }
        String url = ServletUriComponentsBuilder.fromCurrentContextPath().path("/imagenes/" + archivo).toUriString();
        return new ImagenGuardada(url, PREFIJO + archivo);
    }

    @Override
    public void eliminar(String publicId) {
        if (publicId == null || !publicId.startsWith(PREFIJO)) {
            return;
        }
        Path archivo = directorio.resolve(publicId.substring(PREFIJO.length())).normalize();
        if (!archivo.startsWith(directorio)) {
            return;
        }
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            log.warn("No se pudo borrar la imagen {}", archivo);
        }
    }
}
