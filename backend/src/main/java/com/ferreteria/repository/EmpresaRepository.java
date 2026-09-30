package com.ferreteria.repository;

import com.ferreteria.entity.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// JpaRepository ya trae save, findById, findAll, deleteById, etc.
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    // SELECT ... FROM empresa WHERE ruc = ?
    Optional<Empresa> findByRuc(String ruc);

    // Para validar que no se registre dos veces el mismo RUC
    boolean existsByRuc(String ruc);
}
