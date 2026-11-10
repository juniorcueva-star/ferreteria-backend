package com.ferreteria.repository;

import com.ferreteria.entity.MovimientoInventario;
import com.ferreteria.entity.enums.TipoMovimiento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface MovimientoInventarioRepository
        extends JpaRepository<MovimientoInventario, Long>, JpaSpecificationExecutor<MovimientoInventario> {

    boolean existsByProductoIdAndUbicacionId(Long productoId, Long ubicacionId);

    @Override
    @EntityGraph(attributePaths = {"producto", "ubicacion", "usuario", "compra", "venta", "traslado"})
    Page<MovimientoInventario> findAll(Specification<MovimientoInventario> spec, Pageable pageable);

    /** Movimientos de una venta de un tipo (Ej: las salidas VENTA, para devolverlas al anular). */
    List<MovimientoInventario> findByVentaIdAndTipo(Long ventaId, TipoMovimiento tipo);

    /** Fecha de la ultima salida (venta, traslado o ajuste de salida) de cada producto en cada ubicacion. */
    @Query("""
            select m.producto.id as productoId, m.ubicacion.id as ubicacionId, max(m.fecha) as fecha
            from MovimientoInventario m
            where m.cantidad < 0 and (:ubicacionId is null or m.ubicacion.id = :ubicacionId)
            group by m.producto.id, m.ubicacion.id
            """)
    List<UltimaSalida> ultimasSalidas(@Param("ubicacionId") Long ubicacionId);

    interface UltimaSalida {
        Long getProductoId();

        Long getUbicacionId();

        OffsetDateTime getFecha();
    }
}
