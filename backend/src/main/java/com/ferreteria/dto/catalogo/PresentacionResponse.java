package com.ferreteria.dto.catalogo;

import com.ferreteria.entity.Presentacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * @param precioOferta precio de oferta con IGV entre ofertaDesde y ofertaHasta (inclusive); null si no tiene
 * @param escalas      precio por mayor, de menor a mayor cantidad (vacia si no tiene)
 */
public record PresentacionResponse(Long id, String nombre, BigDecimal factor, BigDecimal precioVenta,
                                   String codigoBarras, boolean principal, boolean activo, int decimales,
                                   BigDecimal precioOferta, LocalDate ofertaDesde, LocalDate ofertaHasta,
                                   List<EscalaPrecioResponse> escalas) {

    public static PresentacionResponse desde(Presentacion p) {
        return new PresentacionResponse(p.getId(), p.getNombre(), p.getFactor(), p.getPrecioVenta(),
                p.getCodigoBarras(), p.isPrincipal(), p.isActivo(), p.getDecimales(),
                p.getPrecioOferta(), p.getOfertaDesde(), p.getOfertaHasta(),
                p.getEscalas().stream().map(EscalaPrecioResponse::desde).toList());
    }
}
