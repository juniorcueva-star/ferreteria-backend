package com.ferreteria.repository;

import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.enums.TipoUbicacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface UbicacionRepository extends JpaRepository<Ubicacion, Long>, JpaSpecificationExecutor<Ubicacion> {

    // Ej: listar solo las tiendas activas -> findByTipoAndActivoTrue(TipoUbicacion.TIENDA)
    List<Ubicacion> findByTipoAndActivoTrue(TipoUbicacion tipo);

    boolean existsByNombre(String nombre);

    boolean existsByNombreAndIdNot(String nombre, Long id);

    @Override
    @EntityGraph(attributePaths = "empresa")
    Page<Ubicacion> findAll(Specification<Ubicacion> spec, Pageable pageable);
}
