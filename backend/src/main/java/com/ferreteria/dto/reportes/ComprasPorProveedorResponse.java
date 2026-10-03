package com.ferreteria.dto.reportes;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Compras REGISTRADAS (no anuladas) a un proveedor en el rango de fechas de emision.
 */
public record ComprasPorProveedorResponse(Long proveedorId, String proveedorRuc, String proveedorRazonSocial,
                                          long cantidadCompras, BigDecimal totalComprado, LocalDate ultimaCompra) {
}
