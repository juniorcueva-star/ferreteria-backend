package com.ferreteria.service.imagen;

/**
 * Donde se guardan las fotos de los productos. Hay dos implementaciones:
 * Cloudinary (si se configura CLOUDINARY_URL) o una carpeta local (por defecto, sin cuentas externas).
 */
public interface AlmacenImagenes {

    ImagenGuardada guardar(byte[] contenido, TipoImagen tipo);

    void eliminar(String publicId);
}
