package com.ferreteria.repository;

import com.ferreteria.entity.Venta;
import com.ferreteria.entity.enums.EstadoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    long countByCajaSesionIdAndEstado(Long cajaId, EstadoVenta estado);

    @Query("""
            select coalesce(sum(v.total), 0) from Venta v
            where v.cajaSesion.id = :cajaId and v.estado = com.ferreteria.entity.enums.EstadoVenta.EMITIDA
            """)
    BigDecimal sumarTotalEmitidas(@Param("cajaId") Long cajaId);
}
