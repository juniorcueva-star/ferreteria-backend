package com.ferreteria.dto.reportes;

import com.ferreteria.entity.enums.UnidadBase;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cuanto dinero hay en stock y que productos no salen.
 *
 * @param valorTotal        suma de cantidad x costo de los productos con costo conocido
 * @param productosSinCosto productos con stock pero sin compras registradas (no suman al valor)
 */
public record ValorInventarioResponse(BigDecimal valorTotal, long productos, long productosSinCosto,
                                      List<Fila> filas) {

    /**
     * @param costoUnitario costo por unidad base (ultima compra); null si nunca se compro
     * @param ultimaSalida  ultima venta, traslado o ajuste de salida; null si nunca salio
     * @param diasSinSalida dias desde la ultima salida (null si nunca salio)
     */
    public record Fila(Long productoId, String productoCodigo, String productoNombre, UnidadBase unidadBase,
                       Long ubicacionId, String ubicacionNombre, BigDecimal cantidad, BigDecimal costoUnitario,
                       BigDecimal valor, OffsetDateTime ultimaSalida, Long diasSinSalida) {
    }
}
