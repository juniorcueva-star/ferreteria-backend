package com.ferreteria.repository;

import com.ferreteria.entity.Compra;
import com.ferreteria.entity.enums.EstadoCompra;
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

import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long>, JpaSpecificationExecutor<Compra> {

    // Evita registrar dos veces el mismo comprobante del proveedor
    boolean existsByProveedorIdAndSerieNumeroIgnoreCaseAndEstado(Long proveedorId, String serieNumero,
                                                                 EstadoCompra estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compra c where c.id = :id")
    Optional<Compra> bloquear(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"empresa", "proveedor", "ubicacion", "usuario"})
    Page<Compra> findAll(Specification<Compra> spec, Pageable pageable);
}
