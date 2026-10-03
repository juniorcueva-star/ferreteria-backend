package com.ferreteria.repository;

import com.ferreteria.entity.SerieCorrelativo;
import com.ferreteria.entity.enums.TipoDocumentoVenta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SerieCorrelativoRepository extends JpaRepository<SerieCorrelativo, Long> {

    /** Crea la serie de la tienda con numero 0 si aun no existe (seguro ante creaciones simultaneas). */
    @Modifying
    @Query(value = """
            INSERT INTO serie_correlativo (ubicacion_id, tipo_documento, serie, ultimo_numero)
            VALUES (:ubicacionId, :tipo, :serie, 0)
            ON CONFLICT (ubicacion_id, tipo_documento, serie) DO NOTHING
            """, nativeQuery = true)
    void crearSiNoExiste(@Param("ubicacionId") Long ubicacionId, @Param("tipo") String tipo,
                         @Param("serie") String serie);

    boolean existsByUbicacionIdAndTipoDocumento(Long ubicacionId, TipoDocumentoVenta tipo);

    /** Bloquea la serie para tomar el siguiente numero sin que dos ventas reciban el mismo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SerieCorrelativo> findFirstByUbicacionIdAndTipoDocumentoOrderByIdAsc(Long ubicacionId,
                                                                                 TipoDocumentoVenta tipo);
}
