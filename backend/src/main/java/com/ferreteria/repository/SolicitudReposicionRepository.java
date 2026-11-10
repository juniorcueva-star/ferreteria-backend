package com.ferreteria.repository;

import com.ferreteria.entity.SolicitudReposicion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SolicitudReposicionRepository
        extends JpaRepository<SolicitudReposicion, Long>, JpaSpecificationExecutor<SolicitudReposicion> {

    // Secuencia creada en la migracion V16
    @Query(value = "SELECT nextval('solicitud_reposicion_codigo_seq')", nativeQuery = true)
    long siguienteNumero();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudReposicion s where s.id = :id")
    Optional<SolicitudReposicion> bloquear(@Param("id") Long id);
}
