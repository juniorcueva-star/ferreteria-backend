package com.ferreteria.repository;

import com.ferreteria.dto.reportes.TrasladosPorTiendaResponse;
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

import java.time.OffsetDateTime;
import java.util.Optional;

public interface TrasladoRepository extends JpaRepository<Traslado, Long>, JpaSpecificationExecutor<Traslado> {

    // Secuencia creada en la migracion V3
    @Query(value = "SELECT nextval('traslado_codigo_seq')", nativeQuery = true)
    long siguienteNumero();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Traslado t where t.id = :id")
    Optional<Traslado> bloquear(@Param("id") Long id);

    @Query(value = """
            select new com.ferreteria.dto.reportes.TrasladosPorTiendaResponse(d.id, d.nombre,
                   sum(case when t.estado <> com.ferreteria.entity.enums.EstadoTraslado.ANULADO then 1 else 0 end),
                   sum(case when t.estado = com.ferreteria.entity.enums.EstadoTraslado.RECIBIDO then 1 else 0 end),
                   sum(case when t.estado = com.ferreteria.entity.enums.EstadoTraslado.ENVIADO then 1 else 0 end),
                   sum(case when t.estado = com.ferreteria.entity.enums.EstadoTraslado.ANULADO then 1 else 0 end))
            from Traslado t join t.destino d
            where t.fechaEnvio >= :desde and t.fechaEnvio < :hasta
              and (:origenId is null or t.origen.id = :origenId)
            group by d.id, d.nombre
            order by d.nombre
            """, countQuery = """
            select count(distinct t.destino.id) from Traslado t
            where t.fechaEnvio >= :desde and t.fechaEnvio < :hasta
              and (:origenId is null or t.origen.id = :origenId)
            """)
    Page<TrasladosPorTiendaResponse> trasladosPorDestino(@Param("desde") OffsetDateTime desde,
                                                         @Param("hasta") OffsetDateTime hasta,
                                                         @Param("origenId") Long origenId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"origen", "destino", "usuarioEnvia", "usuarioRecibe"})
    Page<Traslado> findAll(Specification<Traslado> spec, Pageable pageable);
}
