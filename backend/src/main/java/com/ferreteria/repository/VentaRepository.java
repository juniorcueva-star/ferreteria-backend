package com.ferreteria.repository;

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

    @Override
    @EntityGraph(attributePaths = {"ubicacion", "empresa", "usuario", "cajaSesion", "cliente"})
    Page<Venta> findAll(Specification<Venta> spec, Pageable pageable);
}
