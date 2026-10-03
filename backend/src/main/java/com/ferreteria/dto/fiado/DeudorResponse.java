package com.ferreteria.dto.fiado;

import com.ferreteria.entity.enums.TipoDocumentoIdentidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Cliente con deuda de fiado.
 *
 * @param cantidadVentas     ventas a credito con saldo pendiente
 * @param deudaTotal         suma de los saldos pendientes
 * @param deudaMasAntigua    fecha de la venta con saldo mas antigua
 * @param proximoVencimiento vencimiento mas cercano (null si ninguna venta tiene fecha de vencimiento)
 */
public record DeudorResponse(Long clienteId, String clienteNombre, TipoDocumentoIdentidad tipoDocumento,
                             String numeroDocumento, String telefono, long cantidadVentas, BigDecimal deudaTotal,
                             OffsetDateTime deudaMasAntigua, LocalDate proximoVencimiento) {
}
