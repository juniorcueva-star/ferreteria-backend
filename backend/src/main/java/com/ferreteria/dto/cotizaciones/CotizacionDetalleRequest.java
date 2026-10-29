package com.ferreteria.dto.cotizaciones;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record CotizacionDetalleRequest(@NotNull Long presentacionId,
    @NotNull @DecimalMin(value="0", inclusive=false) @DecimalMax("1000000") @Digits(integer=11,fraction=3) BigDecimal cantidad,
    @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal descuento,
    @DecimalMin("-100") @Digits(integer=8,fraction=4) BigDecimal ajustePorcentaje) {}
