package com.ferreteria.entity.enums;

/**
 * Tipo de movimiento del kardex. Debe coincidir con el CHECK ck_mov_tipo.
 * Cada tipo sabe si suma (entrada) o resta (salida) stock, igual que el CHECK ck_mov_signo.
 */
public enum TipoMovimiento {
    INVENTARIO_INICIAL(true),
    COMPRA(true),
    ANULACION_COMPRA(false),
    VENTA(false),
    ANULACION_VENTA(true),
    TRASLADO_SALIDA(false),
    TRASLADO_ENTRADA(true),
    ANULACION_TRASLADO(true),
    AJUSTE_ENTRADA(true),
    AJUSTE_SALIDA(false);

    private final boolean entrada;

    TipoMovimiento(boolean entrada) {
        this.entrada = entrada;
    }

    public boolean esEntrada() {
        return entrada;
    }
}
