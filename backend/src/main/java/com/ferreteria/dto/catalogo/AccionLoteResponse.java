package com.ferreteria.dto.catalogo;

/**
 * Resultado de una accion en lote.
 *
 * @param productosActualizados cantidad de productos distintos que se cambiaron
 */
public record AccionLoteResponse(AccionLoteRequest.AccionLote accion, int productosActualizados) {
}
