package com.ferreteria.service;

import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.Producto;

import java.math.BigDecimal;

/**
 * Linea ya validada: producto, presentacion (puede ser null), cantidad pedida y su equivalente en unidad base.
 */
public record ProductoCantidad(Producto producto, Presentacion presentacion, BigDecimal cantidad,
                               BigDecimal cantidadBase) {
}
