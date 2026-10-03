package com.ferreteria.dto.reportes;

/**
 * Traslados enviados a una tienda (destino) en el rango de fechas, por estado.
 */
public record TrasladosPorTiendaResponse(Long destinoId, String destinoNombre, long cantidadTraslados,
                                         long recibidos, long enCamino, long anulados) {
}
