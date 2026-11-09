package com.ferreteria.repository;

import com.ferreteria.entity.Cotizacion;
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

public interface CotizacionRepository extends JpaRepository<Cotizacion, Long>, JpaSpecificationExecutor<Cotizacion> {

    // Secuencia creada en la migracion V12
    @Query(value = "SELECT nextval('cotizacion_codigo_seq')", nativeQuery = true)
    long siguienteNumero();

    Optional<Cotizacion> findByTokenPublico(String tokenPublico);

    /** Bloquea la cotizacion para que no se edite, anule o convierta dos veces al mismo tiempo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cotizacion c where c.id = :id")
    Optional<Cotizacion> bloquear(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"ubicacion", "cliente", "usuario", "venta"})
    Page<Cotizacion> findAll(Specification<Cotizacion> spec, Pageable pageable);
}
