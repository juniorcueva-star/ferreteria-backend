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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
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

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

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
    public Optional<byte[]> leerParaDocumento(String publicId, String url) {
        if (url == null || !url.contains("/upload/")) {
            return Optional.empty();
        }
        // Cloudinary entrega la miniatura ya en JPEG (aunque se haya subido en WEBP)
        String miniatura = url.replace("/upload/", "/upload/f_jpg,w_160,h_160,c_fill/");
        try {
            HttpResponse<byte[]> respuesta = HTTP.send(HttpRequest.newBuilder(URI.create(miniatura))
                    .timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofByteArray());
            return respuesta.statusCode() == 200 ? Optional.of(respuesta.body()) : Optional.empty();
        } catch (IOException e) {
            log.warn("No se pudo descargar la imagen {}", publicId);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
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
