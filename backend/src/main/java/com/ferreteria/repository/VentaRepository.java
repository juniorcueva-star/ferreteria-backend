package com.ferreteria.repository;

import com.ferreteria.dto.fiado.DeudorResponse;
import com.ferreteria.dto.reportes.VentasPorTiendaResponse;
import com.ferreteria.entity.Venta;
import com.ferreteria.entity.enums.EstadoVenta;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface VentaRepository extends JpaRepository<Venta, Long>, JpaSpecificationExecutor<Venta> {

    long countByCajaSesionIdAndEstado(Long cajaId, EstadoVenta estado);

    @Query("""
            select coalesce(sum(v.total), 0) from Venta v
            where v.cajaSesion.id = :cajaId and v.estado = com.ferreteria.entity.enums.EstadoVenta.EMITIDA
            """)
    BigDecimal sumarTotalEmitidas(@Param("cajaId") Long cajaId);

    /** Bloquea la venta para anularla o abonarle sin que otra operacion la cambie a la vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venta v where v.id = :id")
    Optional<Venta> bloquear(@Param("id") Long id);

    /** Clientes con deuda, de mayor a menor. ubicacionId null = todas las tiendas. */
    @Query(value = """
            select new com.ferreteria.dto.fiado.DeudorResponse(c.id, c.nombre, c.tipoDocumento, c.numeroDocumento,
                   c.telefono, count(v), sum(v.saldoPendiente), min(v.fecha), min(v.fechaVencimiento))
            from Venta v join v.cliente c
            where v.estado = com.ferreteria.entity.enums.EstadoVenta.EMITIDA and v.saldoPendiente > 0
              and (:ubicacionId is null or v.ubicacion.id = :ubicacionId)
            group by c.id, c.nombre, c.tipoDocumento, c.numeroDocumento, c.telefono
            order by sum(v.saldoPendiente) desc, c.id
            """, countQuery = """
            select count(distinct v.cliente.id) from Venta v
            where v.estado = com.ferreteria.entity.enums.EstadoVenta.EMITIDA and v.saldoPendiente > 0
              and (:ubicacionId is null or v.ubicacion.id = :ubicacionId)
            """)
    Page<DeudorResponse> deudores(@Param("ubicacionId") Long ubicacionId, Pageable pageable);

    @Query(value = """
            select new com.ferreteria.dto.reportes.VentasPorTiendaResponse(u.id, u.nombre, e.ruc,
                   sum(case when v.estado = com.ferreteria.entity.enums.EstadoVenta.EMITIDA then 1 else 0 end),
                   coalesce(sum(case when v.estado = com.ferreteria.entity.enums.EstadoVenta.EMITIDA
                                     then v.total else 0 end), 0),
                   coalesce(sum(case when v.estado = com.ferreteria.entity.enums.EstadoVenta.EMITIDA
                                      and v.condicion = com.ferreteria.entity.enums.CondicionVenta.CONTADO
                                     then v.total else 0 end), 0),
                   coalesce(sum(case when v.estado = com.ferreteria.entity.enums.EstadoVenta.EMITIDA
                                      and v.condicion = com.ferreteria.entity.enums.CondicionVenta.CREDITO
                                     then v.total else 0 end), 0),
                   coalesce(sum(v.saldoPendiente), 0),
                   sum(case when v.estado = com.ferreteria.entity.enums.EstadoVenta.ANULADA then 1 else 0 end))
            from Venta v join v.ubicacion u join v.empresa e
            where v.fecha >= :desde and v.fecha < :hasta
              and (:ubicacionId is null or u.id = :ubicacionId)
            group by u.id, u.nombre, e.ruc
            order by u.nombre
            """, countQuery = """
            select count(distinct v.ubicacion.id) from Venta v
            where v.fecha >= :desde and v.fecha < :hasta
              and (:ubicacionId is null or v.ubicacion.id = :ubicacionId)
            """)
    Page<VentasPorTiendaResponse> ventasPorTienda(@Param("desde") OffsetDateTime desde,
                                                  @Param("hasta") OffsetDateTime hasta,
                                                  @Param("ubicacionId") Long ubicacionId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"ubicacion", "empresa", "usuario", "cajaSesion", "cliente"})
    Page<Venta> findAll(Specification<Venta> spec, Pageable pageable);
}
