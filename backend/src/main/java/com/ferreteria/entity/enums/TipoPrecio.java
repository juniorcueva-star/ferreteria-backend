package com.ferreteria.entity.enums;

/**
 * De donde sale el precio de una linea de venta o cotizacion: el de lista, el precio por mayor (por la cantidad) o
 * una oferta vigente. Debe coincidir con los CHECK ck_venta_det_tipo_precio y ck_cotizacion_det_tipo_precio.
 */
public enum TipoPrecio {
    LISTA,
    MAYOR,
    OFERTA
}
