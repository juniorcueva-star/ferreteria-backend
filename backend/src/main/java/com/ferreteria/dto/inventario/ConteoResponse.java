package com.ferreteria.dto.inventario;

import com.ferreteria.entity.enums.UnidadBase;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado del inventario fisico.
 *
 * @param conDiferencia productos cuyo conteo no coincidia con el sistema (se ajustaron)
 */
public record ConteoResponse(Long ubicacionId, String ubicacionNombre, int productosContados, int conDiferencia,
                             List<Linea> lineas) {

    /** @param diferencia contado - sistema (positivo: sobraba; negativo: faltaba) */
    public record Linea(Long productoId, String productoCodigo, String productoNombre, UnidadBase unidadBase,
                        BigDecimal sistema, BigDecimal contado, BigDecimal diferencia) {
    }
}
