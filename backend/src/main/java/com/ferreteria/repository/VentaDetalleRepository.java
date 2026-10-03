package com.ferreteria.repository;

import com.ferreteria.dto.reportes.ProductoVendidoResponse;
import com.ferreteria.entity.VentaDetalle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface VentaDetalleRepository extends JpaRepository<VentaDetalle, Long> {

    String SELECT_PRODUCTOS_VENDIDOS = """
            select new com.ferreteria.dto.reportes.ProductoVendidoResponse(p.id, p.codigo, p.nombre, p.unidadBase,
                   sum(d.cantidadBase), sum(d.subtotal), count(distinct v.id))
            from VentaDetalle d join d.venta v join d.producto p
            where v.estado = com.ferreteria.entity.enums.EstadoVenta.EMITIDA
              and v.fecha >= :desde and v.fecha < :hasta
              and (:ubicacionId is null or v.ubicacion.id = :ubicacionId)
            group by p.id, p.codigo, p.nombre, p.unidadBase
            """;

    String COUNT_PRODUCTOS_VENDIDOS = """
            select count(distinct d.producto.id) from VentaDetalle d join d.venta v
            where v.estado = com.ferreteria.entity.enums.EstadoVenta.EMITIDA
              and v.fecha >= :desde and v.fecha < :hasta
              and (:ubicacionId is null or v.ubicacion.id = :ubicacionId)
            """;

    @Query(value = SELECT_PRODUCTOS_VENDIDOS + " order by sum(d.subtotal) desc, p.id",
            countQuery = COUNT_PRODUCTOS_VENDIDOS)
    Page<ProductoVendidoResponse> masVendidosPorMonto(@Param("desde") OffsetDateTime desde,
                                                      @Param("hasta") OffsetDateTime hasta,
                                                      @Param("ubicacionId") Long ubicacionId, Pageable pageable);

    @Query(value = SELECT_PRODUCTOS_VENDIDOS + " order by sum(d.cantidadBase) desc, p.id",
            countQuery = COUNT_PRODUCTOS_VENDIDOS)
    Page<ProductoVendidoResponse> masVendidosPorCantidad(@Param("desde") OffsetDateTime desde,
                                                         @Param("hasta") OffsetDateTime hasta,
                                                         @Param("ubicacionId") Long ubicacionId, Pageable pageable);
}
