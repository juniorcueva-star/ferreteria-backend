package com.ferreteria.exception;

import java.math.BigDecimal;
import java.util.List;

/**
 * No hay stock suficiente para la salida pedida (409).
 */
public class StockInsuficienteException extends ApiException {

    public StockInsuficienteException(String producto, String ubicacion, BigDecimal disponible, BigDecimal requerido) {
        super(CodigoError.STOCK_INSUFICIENTE,
                "Stock insuficiente de " + producto + " en " + ubicacion,
                List.of("disponible: " + disponible.toPlainString(), "requerido: " + requerido.toPlainString()));
    }
}
