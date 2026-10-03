package com.ferreteria.entity.enums;

/**
 * Comprobante que entrega el proveedor en una compra.
 * Debe coincidir con el CHECK ck_compra_comprobante.
 */
public enum TipoComprobanteCompra {
    FACTURA,
    BOLETA,
    NOTA_VENTA,
    SIN_COMPROBANTE
}
