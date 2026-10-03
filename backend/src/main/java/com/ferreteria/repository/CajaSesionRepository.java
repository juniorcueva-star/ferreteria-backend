package com.ferreteria.repository;

import com.ferreteria.entity.CajaSesion;
import com.ferreteria.entity.enums.EstadoCaja;
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

public interface CajaSesionRepository extends JpaRepository<CajaSesion, Long>, JpaSpecificationExecutor<CajaSesion> {

    Optional<CajaSesion> findByUsuarioIdAndEstado(Long usuarioId, EstadoCaja estado);

    /**
     * Bloqueo compartido (FOR SHARE): varias ventas pueden usar la caja a la vez, pero el cierre
     * (que pide bloqueo exclusivo) espera a que terminen. Asi ninguna venta queda fuera del cuadre.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from CajaSesion c where c.usuario.id = :usuarioId and c.estado = :estado")
    Optional<CajaSesion> bloquearCompartidoPorUsuario(@Param("usuarioId") Long usuarioId,
                                                      @Param("estado") EstadoCaja estado);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from CajaSesion c where c.id = :id")
    Optional<CajaSesion> bloquearCompartido(@Param("id") Long id);

    /** Bloqueo exclusivo para cerrar la caja. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CajaSesion c where c.id = :id")
    Optional<CajaSesion> bloquear(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"ubicacion", "usuario"})
    Page<CajaSesion> findAll(Specification<CajaSesion> spec, Pageable pageable);
}
