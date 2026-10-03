package com.ferreteria.repository;

import com.ferreteria.entity.MetodoPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MetodoPagoRepository extends JpaRepository<MetodoPago, Long> {

    Optional<MetodoPago> findByCodigoIgnoreCase(String codigo);
}
