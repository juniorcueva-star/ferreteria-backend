package com.ferreteria.repository;

import com.ferreteria.entity.MovimientoInventario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MovimientoInventarioRepository
        extends JpaRepository<MovimientoInventario, Long>, JpaSpecificationExecutor<MovimientoInventario> {

    boolean existsByProductoIdAndUbicacionId(Long productoId, Long ubicacionId);

    @Override
    @EntityGraph(attributePaths = {"producto", "ubicacion", "usuario", "compra", "venta", "traslado"})
    Page<MovimientoInventario> findAll(Specification<MovimientoInventario> spec, Pageable pageable);
}
