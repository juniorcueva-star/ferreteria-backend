package com.ferreteria.repository;

import com.ferreteria.entity.Traslado;
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

public interface TrasladoRepository extends JpaRepository<Traslado, Long>, JpaSpecificationExecutor<Traslado> {

    // Secuencia creada en la migracion V3
    @Query(value = "SELECT nextval('traslado_codigo_seq')", nativeQuery = true)
    long siguienteNumero();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Traslado t where t.id = :id")
    Optional<Traslado> bloquear(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"origen", "destino", "usuarioEnvia", "usuarioRecibe"})
    Page<Traslado> findAll(Specification<Traslado> spec, Pageable pageable);
}
