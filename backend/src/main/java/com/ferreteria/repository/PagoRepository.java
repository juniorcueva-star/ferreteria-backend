package com.ferreteria.repository;

import com.ferreteria.dto.caja.CobroPorMetodo;
import com.ferreteria.entity.Pago;
import com.ferreteria.entity.enums.TipoPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    /** Cobros validos de una caja agrupados por metodo de pago. */
    @Query("""
            select new com.ferreteria.dto.caja.CobroPorMetodo(m.codigo, m.nombre, m.efectivo, sum(p.monto))
            from Pago p join p.metodoPago m
            where p.cajaSesion.id = :cajaId and p.estado = com.ferreteria.entity.enums.EstadoPago.VALIDO
            group by m.codigo, m.nombre, m.efectivo
            order by m.codigo
            """)
    List<CobroPorMetodo> cobrosPorMetodo(@Param("cajaId") Long cajaId);

    @Query("""
            select coalesce(sum(p.monto), 0) from Pago p
            where p.cajaSesion.id = :cajaId and p.tipo = :tipo
              and p.estado = com.ferreteria.entity.enums.EstadoPago.VALIDO
            """)
    BigDecimal sumarPorTipo(@Param("cajaId") Long cajaId, @Param("tipo") TipoPago tipo);
}
