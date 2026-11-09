package com.ferreteria.repository;

import com.ferreteria.entity.Encargo;
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

public interface EncargoRepository extends JpaRepository<Encargo, Long>, JpaSpecificationExecutor<Encargo> {

    // Secuencia creada en la migracion V11
    @Query(value = "SELECT nextval('encargo_codigo_seq')", nativeQuery = true)
    long siguienteNumero();

    /** Bloquea el encargo para que no se entregue o anule dos veces al mismo tiempo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Encargo e where e.id = :id")
    Optional<Encargo> bloquear(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"ubicacion", "cliente", "producto", "presentacion", "venta", "usuario"})
    Page<Encargo> findAll(Specification<Encargo> spec, Pageable pageable);
}
