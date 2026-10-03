package com.ferreteria.dto.compras;

import com.ferreteria.entity.enums.TipoComprobanteCompra;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Compra a un proveedor. La mercaderia entra al stock de la ubicacion indicada.
 *
 * @param empresaId   empresa (RUC) que compra
 * @param fechaEmision fecha del comprobante; no puede ser futura (se compara con el dia de hoy en Lima)
 * @param ubicacionId donde entra la mercaderia; el ALMACENERO puede omitirla (se usa su almacen), el ADMIN debe indicarla
 */
public record CompraRequest(
        @NotNull Long empresaId,
        @NotNull Long proveedorId,
        Long ubicacionId,
        @NotNull TipoComprobanteCompra tipoComprobante,
        @Size(max = 30) String serieNumero,
        @NotNull LocalDate fechaEmision,
        @Size(max = 300) String observacion,
        @NotEmpty @Size(max = 200) List<@Valid @NotNull CompraDetalleRequest> detalles) {
}
