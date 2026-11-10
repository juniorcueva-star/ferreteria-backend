package com.ferreteria.dto.reportes;

import com.ferreteria.entity.enums.UnidadBase;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Producto que conviene comprar: llego a su minimo en el almacen.
 *
 * @param cantidadSugerida hasta el doble del minimo (2 x minimo - stock), en unidad base
 * @param ultimoCosto      costo por unidad base de la ultima compra (null si nunca se compro)
 * @param montoEstimado    cantidad sugerida x ultimo costo (null si no hay costo)
 */
public record PedidoSugeridoResponse(Long productoId, String productoCodigo, String productoNombre,
                                     UnidadBase unidadBase, BigDecimal stock, BigDecimal stockMinimo,
                                     BigDecimal cantidadSugerida, String ultimoProveedor, LocalDate fechaUltimaCompra,
                                     BigDecimal ultimoCosto, BigDecimal montoEstimado) {
}
