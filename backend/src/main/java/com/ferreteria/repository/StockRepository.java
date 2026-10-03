package com.ferreteria.repository;

import com.ferreteria.entity.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long>, JpaSpecificationExecutor<Stock> {

    /**
     * Crea la fila de stock en 0 si todavia no existe. ON CONFLICT evita el error cuando dos
     * transacciones intentan crearla al mismo tiempo.
     */
    @Modifying
    @Query(value = """
            INSERT INTO stock (producto_id, ubicacion_id, cantidad, stock_minimo, updated_at)
            VALUES (:productoId, :ubicacionId, 0, 0, NOW())
            ON CONFLICT (producto_id, ubicacion_id) DO NOTHING
            """, nativeQuery = true)
    void crearSiNoExiste(@Param("productoId") Long productoId, @Param("ubicacionId") Long ubicacionId);

    /**
     * SELECT ... FOR UPDATE: bloquea la fila hasta que termine la transaccion. Otra venta del mismo
     * producto en la misma ubicacion espera aqui y luego lee el stock ya actualizado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.producto.id = :productoId and s.ubicacion.id = :ubicacionId")
    Optional<Stock> bloquear(@Param("productoId") Long productoId, @Param("ubicacionId") Long ubicacionId);

    Optional<Stock> findByProductoIdAndUbicacionId(Long productoId, Long ubicacionId);

    @Override
    @EntityGraph(attributePaths = {"producto", "ubicacion"})
    Page<Stock> findAll(Specification<Stock> spec, Pageable pageable);
}
