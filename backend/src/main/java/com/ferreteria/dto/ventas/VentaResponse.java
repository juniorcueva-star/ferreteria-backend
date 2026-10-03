package com.ferreteria.dto.ventas;

import com.ferreteria.entity.Venta;
import com.ferreteria.entity.VentaDetalle;
import com.ferreteria.entity.enums.CondicionVenta;
import com.ferreteria.entity.enums.EstadoVenta;
import com.ferreteria.entity.enums.TipoDocumentoVenta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Venta con detalle y pagos (en los listados van vacios).
 *
 * @param numeroDocumento serie y numero, Ej: NV01-000245
 * @param vuelto          solo al registrar una venta al contado pagada de mas en efectivo; si no, 0
 */
public record VentaResponse(Long id, TipoDocumentoVenta tipoDocumento, String numeroDocumento, OffsetDateTime fecha,
                            Long ubicacionId, String ubicacionNombre, String empresaRuc, String empresaRazonSocial,
                            String vendedor, Long cajaId, Long clienteId, String clienteNombre,
                            CondicionVenta condicion, LocalDate fechaVencimiento, BigDecimal subtotal,
                            BigDecimal igv, BigDecimal descuento, BigDecimal total, BigDecimal saldoPendiente,
                            EstadoVenta estado, String motivoAnulacion, BigDecimal vuelto,
                            List<Detalle> detalles, List<PagoResponse> pagos) {

    public record Detalle(Long productoId, Long presentacionId, String descripcion, BigDecimal cantidad,
                          BigDecimal factor, BigDecimal cantidadBase, BigDecimal precioUnitario,
                          BigDecimal descuento, BigDecimal subtotal) {

        static Detalle desde(VentaDetalle d) {
            return new Detalle(d.getProducto().getId(), d.getPresentacion().getId(), d.getDescripcion(),
                    d.getCantidad(), d.getFactor(), d.getCantidadBase(), d.getPrecioUnitario(), d.getDescuento(),
                    d.getSubtotal());
        }
    }

    public static VentaResponse resumen(Venta v) {
        return crear(v, BigDecimal.ZERO, List.of(), List.of());
    }

    public static VentaResponse completa(Venta v) {
        return completa(v, BigDecimal.ZERO);
    }

    public static VentaResponse completa(Venta v, BigDecimal vuelto) {
        return crear(v, vuelto, v.getDetalles().stream().map(Detalle::desde).toList(),
                v.getPagos().stream().map(PagoResponse::desde).toList());
    }

    private static VentaResponse crear(Venta v, BigDecimal vuelto, List<Detalle> detalles, List<PagoResponse> pagos) {
        return new VentaResponse(v.getId(), v.getTipoDocumento(), v.getNumeroDocumento(), v.getFecha(),
                v.getUbicacion().getId(), v.getUbicacion().getNombre(), v.getEmpresa().getRuc(),
                v.getEmpresa().getRazonSocial(), v.getUsuario().getUsername(), v.getCajaSesion().getId(),
                v.getCliente() == null ? null : v.getCliente().getId(),
                v.getCliente() == null ? null : v.getCliente().getNombre(),
                v.getCondicion(), v.getFechaVencimiento(), v.getSubtotal(), v.getIgv(), v.getDescuento(),
                v.getTotal(), v.getSaldoPendiente(), v.getEstado(), v.getMotivoAnulacion(), vuelto, detalles, pagos);
    }
}
