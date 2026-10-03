package com.ferreteria.dto.ventas;

import com.ferreteria.entity.enums.CondicionVenta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Venta en la tienda de la caja abierta del usuario.
 * <ul>
 *   <li>CONTADO: los pagos deben cubrir el total. Si se paga de mas en efectivo, la diferencia es el vuelto.</li>
 *   <li>CREDITO (fiado): requiere cliente; los pagos son un adelanto opcional y el resto queda como deuda.</li>
 * </ul>
 */
public record VentaRequest(
        @NotNull CondicionVenta condicion,
        Long clienteId,
        LocalDate fechaVencimiento,
        @NotEmpty @Size(max = 100) List<@Valid @NotNull VentaDetalleRequest> detalles,
        @Size(max = 10) List<@Valid @NotNull PagoRequest> pagos) {
}
