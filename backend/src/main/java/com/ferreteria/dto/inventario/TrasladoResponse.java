package com.ferreteria.dto.inventario;

import com.ferreteria.entity.Traslado;
import com.ferreteria.entity.TrasladoDetalle;
import com.ferreteria.entity.enums.EstadoTraslado;
import com.ferreteria.entity.enums.UnidadBase;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Traslado con su detalle (vacio en los listados).
 */
public record TrasladoResponse(Long id, String codigo, Long origenId, String origenNombre, Long destinoId,
                               String destinoNombre, EstadoTraslado estado, String usuarioEnvia,
                               String usuarioRecibe, OffsetDateTime fechaEnvio, OffsetDateTime fechaRecepcion,
                               String observacion, List<Detalle> detalles) {

    public record Detalle(Long productoId, String productoNombre, UnidadBase unidadBase, String presentacionNombre,
                          BigDecimal cantidad, BigDecimal cantidadBase) {

        static Detalle desde(TrasladoDetalle d) {
            return new Detalle(d.getProducto().getId(), d.getProducto().getNombre(), d.getProducto().getUnidadBase(),
                    d.getPresentacion() == null ? null : d.getPresentacion().getNombre(),
                    d.getCantidad(), d.getCantidadBase());
        }
    }

    public static TrasladoResponse resumen(Traslado t) {
        return crear(t, List.of());
    }

    public static TrasladoResponse completo(Traslado t) {
        return crear(t, t.getDetalles().stream().map(Detalle::desde).toList());
    }

    private static TrasladoResponse crear(Traslado t, List<Detalle> detalles) {
        return new TrasladoResponse(t.getId(), t.getCodigo(), t.getOrigen().getId(), t.getOrigen().getNombre(),
                t.getDestino().getId(), t.getDestino().getNombre(), t.getEstado(), t.getUsuarioEnvia().getUsername(),
                t.getUsuarioRecibe() == null ? null : t.getUsuarioRecibe().getUsername(),
                t.getFechaEnvio(), t.getFechaRecepcion(), t.getObservacion(), detalles);
    }
}
