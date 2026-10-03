package com.ferreteria.repository;

import com.ferreteria.entity.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// JpaRepository ya trae save, findById, findAll, deleteById, etc.
public interface EmpresaRepository extends JpaRepository<Empresa, Long>, JpaSpecificationExecutor<Empresa> {

    // Para validar que no se registre dos veces el mismo RUC
    boolean existsByRuc(String ruc);

    boolean existsByRucAndIdNot(String ruc, Long id);
}
