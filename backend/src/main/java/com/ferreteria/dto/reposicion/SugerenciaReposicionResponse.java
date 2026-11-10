package com.ferreteria.dto.reposicion;

import com.ferreteria.entity.enums.UnidadBase;

import java.math.BigDecimal;

/**
 * Producto de una tienda que llego a su stock minimo y cuanto conviene pedir.
 *
 * @param cantidadSugerida hasta el doble del minimo (2 x minimo - stock), limitada a lo que hay en los almacenes
 * @param stockAlmacen     suma del stock de los almacenes
 */
public record SugerenciaReposicionResponse(Long productoId, String productoCodigo, String productoNombre,
                                           UnidadBase unidadBase, BigDecimal stockTienda, BigDecimal stockMinimo,
                                           BigDecimal stockAlmacen, BigDecimal cantidadSugerida) {
}
