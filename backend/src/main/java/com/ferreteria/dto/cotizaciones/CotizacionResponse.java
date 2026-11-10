package com.ferreteria.dto.cotizaciones;

import com.ferreteria.entity.Cliente;
import com.ferreteria.entity.Cotizacion;
import com.ferreteria.entity.CotizacionDetalle;
import com.ferreteria.entity.enums.EstadoCotizacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cotizacion con su detalle (vacio en los listados).
 *
 * @param ventaId     nota de venta creada al convertirla (null si no se convirtio)
 */
public record CotizacionResponse(Long id, String codigo, Long ubicacionId, String ubicacionNombre, String empresaRuc,
                                 String empresaRazonSocial, Long clienteId, String clienteNombre,
                                 String clienteDocumento, String usuario, OffsetDateTime fecha,
                                 LocalDate fechaValidez, EstadoCotizacion estado, String observacion,
                                 BigDecimal subtotal, BigDecimal igv, BigDecimal descuento, BigDecimal total,
                                 Long ventaId, String ventaNumero, List<Detalle> detalles) {

    public record Detalle(Long productoId, Long presentacionId, String descripcion, BigDecimal cantidad,
                          BigDecimal factor, BigDecimal cantidadBase, BigDecimal precioUnitario, BigDecimal descuento,
                          BigDecimal subtotal, int decimales, Long completoId, String composicion) {

        static Detalle desde(CotizacionDetalle d) {
            boolean completo = d.getCompleto() != null;
            return new Detalle(d.getProducto().getId(), completo ? null : d.getPresentacion().getId(),
                    d.getDescripcion(), d.getCantidad(), d.getFactor(), d.getCantidadBase(), d.getPrecioUnitario(),
                    d.getDescuento(), d.getSubtotal(), completo ? 0 : d.getPresentacion().getDecimales(),
                    completo ? d.getCompleto().getId() : null, d.getComposicion());
        }
    }

    public static CotizacionResponse resumen(Cotizacion c) {
        return crear(c, List.of());
    }

    public static CotizacionResponse completa(Cotizacion c) {
        return crear(c, c.getDetalles().stream().map(Detalle::desde).toList());
    }

    private static CotizacionResponse crear(Cotizacion c, List<Detalle> detalles) {
        Cliente cliente = c.getCliente();
        return new CotizacionResponse(c.getId(), c.getCodigo(), c.getUbicacion().getId(), c.getUbicacion().getNombre(),
                c.getUbicacion().getEmpresa().getRuc(), c.getUbicacion().getEmpresa().getRazonSocial(),
                cliente == null ? null : cliente.getId(), cliente == null ? null : cliente.getNombre(),
                cliente == null || cliente.getNumeroDocumento() == null ? null
                        : cliente.getTipoDocumento() + " " + cliente.getNumeroDocumento(),
                c.getUsuario().getUsername(), c.getCreatedAt(), c.getFechaValidez(), c.getEstado(),
                c.getObservacion(), c.getSubtotal(), c.getIgv(), c.getDescuento(), c.getTotal(),
                c.getVenta() == null ? null : c.getVenta().getId(),
                c.getVenta() == null ? null : c.getVenta().getNumeroDocumento(), detalles);
    }
}
