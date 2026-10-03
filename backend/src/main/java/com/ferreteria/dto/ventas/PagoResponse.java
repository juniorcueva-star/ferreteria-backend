package com.ferreteria.dto.ventas;

import com.ferreteria.entity.Pago;
import com.ferreteria.entity.enums.EstadoPago;
import com.ferreteria.entity.enums.TipoPago;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PagoResponse(Long id, TipoPago tipo, String metodoPago, BigDecimal monto, String numeroOperacion,
                           Long cajaId, String usuario, OffsetDateTime fecha, EstadoPago estado) {

    public static PagoResponse desde(Pago p) {
        return new PagoResponse(p.getId(), p.getTipo(), p.getMetodoPago().getCodigo(), p.getMonto(),
                p.getNumeroOperacion(), p.getCajaSesion().getId(), p.getUsuario().getUsername(), p.getFecha(),
                p.getEstado());
    }
}
