package com.ferreteria.repository;

import com.ferreteria.entity.UnidadMedida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, Long> {

    List<UnidadMedida> findByActivoTrueOrderByIdAsc();
}
