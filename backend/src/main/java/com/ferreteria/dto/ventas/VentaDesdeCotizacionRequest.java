package com.ferreteria.dto.ventas;

import com.ferreteria.entity.enums.CondicionVenta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** La cotizacion es la fuente exclusiva de sus lineas y snapshots comerciales. */
public record VentaDesdeCotizacionRequest(@NotNull CondicionVenta condicion, LocalDate fechaVencimiento,
                                          @Size(max = 10) List<@Valid @NotNull PagoRequest> pagos) {
}
