package com.ferreteria.service.stock;

import com.ferreteria.entity.Compra;
import com.ferreteria.entity.Traslado;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.Venta;

/**
 * Documento que origina un movimiento de kardex y quien lo hace.
 * Solo uno de compra, venta o traslado tiene valor; los ajustes llevan motivo.
 */
public record OrigenMovimiento(Usuario usuario, Compra compra, Venta venta, Traslado traslado, String motivo) {

    public static OrigenMovimiento compra(Compra compra, Usuario usuario, String motivo) {
        return new OrigenMovimiento(usuario, compra, null, null, motivo);
    }

    public static OrigenMovimiento venta(Venta venta, Usuario usuario, String motivo) {
        return new OrigenMovimiento(usuario, null, venta, null, motivo);
    }

    public static OrigenMovimiento traslado(Traslado traslado, Usuario usuario) {
        return new OrigenMovimiento(usuario, null, null, traslado, null);
    }

    public static OrigenMovimiento ajuste(Usuario usuario, String motivo) {
        return new OrigenMovimiento(usuario, null, null, null, motivo);
    }
}
