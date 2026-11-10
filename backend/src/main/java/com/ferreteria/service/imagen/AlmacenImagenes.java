package com.ferreteria.service.imagen;

import java.util.Optional;

/**
 * Donde se guardan las fotos de los productos. Hay dos implementaciones:
 * Cloudinary (si se configura CLOUDINARY_URL) o una carpeta local (por defecto, sin cuentas externas).
 */
public interface AlmacenImagenes {

    ImagenGuardada guardar(byte[] contenido, TipoImagen tipo);

    void eliminar(String publicId);

    /**
     * Bytes de una foto guardada en un formato que se puede poner en un PDF (JPEG o PNG), para la lista de
     * precios. Vacio si no se puede leer o el formato no sirve: el documento sale sin esa foto.
     */
    Optional<byte[]> leerParaDocumento(String publicId, String url);
}
