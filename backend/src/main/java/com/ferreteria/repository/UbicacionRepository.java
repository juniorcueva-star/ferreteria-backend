package com.ferreteria.repository;

import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.enums.TipoUbicacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UbicacionRepository extends JpaRepository<Ubicacion, Long> {

    // Ej: listar solo las tiendas activas -> findByTipoAndActivoTrue(TipoUbicacion.TIENDA)
    List<Ubicacion> findByTipoAndActivoTrue(TipoUbicacion tipo);

    boolean existsByNombre(String nombre);
}
