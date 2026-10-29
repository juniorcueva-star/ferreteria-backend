package com.ferreteria.repository;

import com.ferreteria.entity.HistorialPrecioPresentacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialPrecioPresentacionRepository extends JpaRepository<HistorialPrecioPresentacion, Long> {
    List<HistorialPrecioPresentacion> findByProductoIdOrderByFechaDesc(Long productoId);
}
