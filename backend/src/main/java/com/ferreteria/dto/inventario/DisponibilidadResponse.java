package com.ferreteria.dto.inventario;

import com.ferreteria.entity.Stock;
import java.math.BigDecimal;

/** Solo disponibilidad visible para venta; no concede acceso a movimientos ni a operaciones ajenas. */
public record DisponibilidadResponse(Long productoId, Long ubicacionId, String ubicacionNombre, BigDecimal cantidad) {
    public static DisponibilidadResponse desde(Stock stock) {
        return new DisponibilidadResponse(stock.getProducto().getId(), stock.getUbicacion().getId(),
                stock.getUbicacion().getNombre(), stock.getCantidad());
    }
}
