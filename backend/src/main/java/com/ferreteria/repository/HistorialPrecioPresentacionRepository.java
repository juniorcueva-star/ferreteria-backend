package com.ferreteria.repository;

import com.ferreteria.entity.HistorialPrecioPresentacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistorialPrecioPresentacionRepository extends JpaRepository<HistorialPrecioPresentacion, Long> {

    @EntityGraph(attributePaths = "usuario")
    Page<HistorialPrecioPresentacion> findByPresentacionId(Long presentacionId, Pageable pageable);
}
