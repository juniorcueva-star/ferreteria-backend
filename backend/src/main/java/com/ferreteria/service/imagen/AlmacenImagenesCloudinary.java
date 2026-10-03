package com.ferreteria.service.imagen;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Guarda las fotos en Cloudinary. Se activa solo si existe la variable CLOUDINARY_URL
 * (formato cloudinary://API_KEY:API_SECRET@CLOUD_NAME). En la BD solo se guarda la URL y el public_id.
 */
@Slf4j
@Component
@ConditionalOnExpression("!'${app.imagenes.cloudinary-url:}'.isBlank()")
public class AlmacenImagenesCloudinary implements AlmacenImagenes {

    private static final String CARPETA = "ferreteria/productos";

    private final Cloudinary cloudinary;

    public AlmacenImagenesCloudinary(@Value("${app.imagenes.cloudinary-url}") String cloudinaryUrl) {
        this.cloudinary = new Cloudinary(cloudinaryUrl);
    }

    @Override
    public ImagenGuardada guardar(byte[] contenido, TipoImagen tipo) {
        try {
            Map<?, ?> resultado = cloudinary.uploader().upload(contenido,
                    ObjectUtils.asMap("folder", CARPETA, "resource_type", "image"));
            return new ImagenGuardada((String) resultado.get("secure_url"), (String) resultado.get("public_id"));
        } catch (IOException | RuntimeException e) {
            log.error("Error al subir la imagen a Cloudinary", e);
            throw new ApiException(CodigoError.SERVICIO_EXTERNO, "No se pudo subir la imagen a Cloudinary");
        }
    }

    @Override
    public void eliminar(String publicId) {
        if (publicId == null || publicId.startsWith(AlmacenImagenesLocal.PREFIJO)) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo borrar la imagen {} de Cloudinary", publicId);
        }
    }
}
