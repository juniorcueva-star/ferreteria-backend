package com.ferreteria.entity.enums;

/**
 * Documento que se emite en una venta. En la Fase 1 solo se usa NOTA_VENTA.
 * Debe coincidir con el CHECK ck_venta_tipo y ck_serie_tipo.
 */
public enum TipoDocumentoVenta {
    NOTA_VENTA,
    BOLETA,
    FACTURA
}
