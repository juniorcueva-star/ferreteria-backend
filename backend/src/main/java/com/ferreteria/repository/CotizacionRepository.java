package com.ferreteria.repository;
import com.ferreteria.entity.Cotizacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface CotizacionRepository extends JpaRepository<Cotizacion, Long>, JpaSpecificationExecutor<Cotizacion> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from Cotizacion c where c.id = :id") Optional<Cotizacion> bloquear(@Param("id") Long id);
}
