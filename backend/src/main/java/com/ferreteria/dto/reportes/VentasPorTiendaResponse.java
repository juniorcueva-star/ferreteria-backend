package com.ferreteria.dto.reportes;

import java.math.BigDecimal;

/**
 * Ventas de una tienda en el rango de fechas. Los montos solo consideran ventas EMITIDAS (no anuladas).
 *
 * @param totalCredito    total de las ventas al fiado (incluye lo que ya se abono)
 * @param saldoPendiente  deuda que aun queda de esas ventas al fiado
 */
public record VentasPorTiendaResponse(Long ubicacionId, String ubicacionNombre, String empresaRuc,
                                      long cantidadVentas, BigDecimal totalVendido, BigDecimal totalContado,
                                      BigDecimal totalCredito, BigDecimal saldoPendiente, long cantidadAnuladas) {
}
