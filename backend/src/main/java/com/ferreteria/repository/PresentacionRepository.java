package com.ferreteria.repository;

import com.ferreteria.entity.Presentacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PresentacionRepository extends JpaRepository<Presentacion, Long> {

    boolean existsByCodigoBarras(String codigoBarras);

    boolean existsByCodigoBarrasAndIdNot(String codigoBarras, Long id);

    @EntityGraph(attributePaths = "producto")
    Optional<Presentacion> findByCodigoBarras(String codigoBarras);

    // Carga las presentaciones de una venta con su producto en una sola consulta
    @EntityGraph(attributePaths = "producto")
    List<Presentacion> findByIdIn(Collection<Long> ids);
}
