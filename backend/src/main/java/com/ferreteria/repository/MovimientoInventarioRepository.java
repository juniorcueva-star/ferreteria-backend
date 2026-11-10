package com.ferreteria.repository;

import com.ferreteria.entity.MovimientoInventario;
import com.ferreteria.entity.enums.TipoMovimiento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface MovimientoInventarioRepository
        extends JpaRepository<MovimientoInventario, Long>, JpaSpecificationExecutor<MovimientoInventario> {

    boolean existsByProductoIdAndUbicacionId(Long productoId, Long ubicacionId);

    @Override
    @EntityGraph(attributePaths = {"producto", "ubicacion", "usuario", "compra", "venta", "traslado"})
    Page<MovimientoInventario> findAll(Specification<MovimientoInventario> spec, Pageable pageable);

    /** Movimientos de una venta de un tipo (Ej: las salidas VENTA, para devolverlas al anular). */
    List<MovimientoInventario> findByVentaIdAndTipo(Long ventaId, TipoMovimiento tipo);
}
