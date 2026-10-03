package com.ferreteria.dto.caja;

import com.ferreteria.entity.CajaSesion;
import com.ferreteria.entity.enums.EstadoCaja;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Sesion de caja. En los listados el resumen va en null; al consultar una caja o cerrarla se incluye.
 */
public record CajaResponse(Long id, Long ubicacionId, String ubicacionNombre, Long usuarioId, String usuario,
                           OffsetDateTime fechaApertura, BigDecimal montoApertura, OffsetDateTime fechaCierre,
                           BigDecimal efectivoEsperado, BigDecimal efectivoContado, BigDecimal diferencia,
                           EstadoCaja estado, Resumen resumen) {

    /**
     * Movimiento de la caja.
     *
     * @param cantidadVentas   ventas emitidas (no anuladas) en esta caja
     * @param totalVendido     suma de esas ventas (contado + credito)
     * @param totalCredito     parte de lo vendido que quedo como deuda (fiado) al momento de vender
     * @param totalCobrado     dinero recibido en la caja (pagos de ventas y abonos de fiado, todos los metodos)
     * @param totalAbonos      parte de lo cobrado que corresponde a abonos de fiados anteriores
     * @param efectivoEsperado monto de apertura + cobros en efectivo (lo que deberia haber en el cajon)
     */
    public record Resumen(long cantidadVentas, BigDecimal totalVendido, BigDecimal totalCredito,
                          BigDecimal totalCobrado, BigDecimal totalAbonos, List<CobroPorMetodo> cobrosPorMetodo,
                          BigDecimal efectivoEsperado) {
    }

    public static CajaResponse desde(CajaSesion c, Resumen resumen) {
        return new CajaResponse(c.getId(), c.getUbicacion().getId(), c.getUbicacion().getNombre(),
                c.getUsuario().getId(), c.getUsuario().getUsername(), c.getFechaApertura(), c.getMontoApertura(),
                c.getFechaCierre(), c.getEfectivoEsperado(), c.getEfectivoContado(), c.getDiferencia(),
                c.getEstado(), resumen);
    }
}
