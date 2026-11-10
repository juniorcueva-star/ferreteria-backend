package com.ferreteria.repository;

import com.ferreteria.dto.inventario.CantidadEnUbicacion;
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

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
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

    /** Stock de varios productos en todas las ubicaciones activas, en una sola consulta. */
    @Query("""
            select new com.ferreteria.dto.inventario.CantidadEnUbicacion(s.producto.id, u.id, s.cantidad, s.stockMinimo)
            from Stock s join s.ubicacion u
            where s.producto.id in :productoIds and u.activo = true
            """)
    List<CantidadEnUbicacion> cantidadesPorUbicacion(@Param("productoIds") Collection<Long> productoIds);

    Optional<Stock> findByProductoIdAndUbicacionId(Long productoId, Long ubicacionId);

    @Override
    @EntityGraph(attributePaths = {"producto", "ubicacion"})
    Page<Stock> findAll(Specification<Stock> spec, Pageable pageable);

    /** Productos activos de una ubicacion que llegaron a su stock minimo (minimo mayor que 0). */
    @Query("""
            select s from Stock s join fetch s.producto p
            where s.ubicacion.id = :ubicacionId and s.stockMinimo > 0 and s.cantidad <= s.stockMinimo
              and p.activo = true
            order by p.nombre
            """)
    List<Stock> enMinimo(@Param("ubicacionId") Long ubicacionId);

    /** Stock de cada producto sumando los almacenes. */
    @Query("""
            select s.producto.id as productoId, sum(s.cantidad) as cantidad from Stock s
            where s.ubicacion.tipo = com.ferreteria.entity.enums.TipoUbicacion.ALMACEN
              and s.producto.id in :productoIds
            group by s.producto.id
            """)
    List<StockAlmacen> enAlmacenes(@Param("productoIds") Collection<Long> productoIds);

    interface StockAlmacen {
        Long getProductoId();

        BigDecimal getCantidad();
    }

    /** Stock con cantidad mayor que 0 de una ubicacion (o de todas si es null), con su producto. */
    @Query("""
            select s from Stock s join fetch s.producto p join fetch s.ubicacion u
            where s.cantidad > 0 and (:ubicacionId is null or u.id = :ubicacionId)
            order by p.nombre, u.nombre
            """)
    List<Stock> conCantidad(@Param("ubicacionId") Long ubicacionId);
}
