package com.ferreteria.repository;

import com.ferreteria.dto.reportes.ComprasPorProveedorResponse;
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

import java.time.LocalDate;
import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long>, JpaSpecificationExecutor<Compra> {

    // Evita registrar dos veces el mismo comprobante del proveedor
    boolean existsByProveedorIdAndSerieNumeroIgnoreCaseAndEstado(Long proveedorId, String serieNumero,
                                                                 EstadoCompra estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compra c where c.id = :id")
    Optional<Compra> bloquear(@Param("id") Long id);

    @Query(value = """
            select new com.ferreteria.dto.reportes.ComprasPorProveedorResponse(p.id, p.ruc, p.razonSocial,
                   count(c), sum(c.total), max(c.fechaEmision))
            from Compra c join c.proveedor p
            where c.estado = com.ferreteria.entity.enums.EstadoCompra.REGISTRADA
              and c.fechaEmision >= :desde and c.fechaEmision <= :hasta
              and (:ubicacionId is null or c.ubicacion.id = :ubicacionId)
              and (:empresaId is null or c.empresa.id = :empresaId)
            group by p.id, p.ruc, p.razonSocial
            order by sum(c.total) desc, p.id
            """, countQuery = """
            select count(distinct c.proveedor.id) from Compra c
            where c.estado = com.ferreteria.entity.enums.EstadoCompra.REGISTRADA
              and c.fechaEmision >= :desde and c.fechaEmision <= :hasta
              and (:ubicacionId is null or c.ubicacion.id = :ubicacionId)
              and (:empresaId is null or c.empresa.id = :empresaId)
            """)
    Page<ComprasPorProveedorResponse> comprasPorProveedor(@Param("desde") LocalDate desde,
                                                          @Param("hasta") LocalDate hasta,
                                                          @Param("ubicacionId") Long ubicacionId,
                                                          @Param("empresaId") Long empresaId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"empresa", "proveedor", "ubicacion", "usuario"})
    Page<Compra> findAll(Specification<Compra> spec, Pageable pageable);
}
