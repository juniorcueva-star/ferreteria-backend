package com.ferreteria.repository;

import com.ferreteria.entity.Cliente;
import com.ferreteria.entity.enums.TipoDocumentoIdentidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClienteRepository extends JpaRepository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {

    boolean existsByTipoDocumentoAndNumeroDocumento(TipoDocumentoIdentidad tipo, String numero);

    boolean existsByTipoDocumentoAndNumeroDocumentoAndIdNot(TipoDocumentoIdentidad tipo, String numero, Long id);
}
