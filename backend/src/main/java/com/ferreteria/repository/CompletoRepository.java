package com.ferreteria.repository;

import com.ferreteria.entity.Completo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompletoRepository extends JpaRepository<Completo, Long> {

    boolean existsByProductoIdAndNombreIgnoreCase(Long productoId, String nombre);

    boolean existsByProductoIdAndNombreIgnoreCaseAndIdNot(Long productoId, String nombre, Long id);
}
