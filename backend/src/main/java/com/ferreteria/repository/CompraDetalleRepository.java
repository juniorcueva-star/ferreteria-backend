package com.ferreteria.repository;

import com.ferreteria.entity.CompraDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CompraDetalleRepository extends JpaRepository<CompraDetalle, Long> {

    /** Compras registradas (no anuladas) de estos productos, la mas reciente primero. */
    @Query("""
            select d from CompraDetalle d join fetch d.compra c join fetch c.proveedor
            where d.producto.id in :productoIds and c.estado = com.ferreteria.entity.enums.EstadoCompra.REGISTRADA
            order by c.fechaEmision desc, c.id desc
            """)
    List<CompraDetalle> comprasRecientes(@Param("productoIds") Collection<Long> productoIds);
}
