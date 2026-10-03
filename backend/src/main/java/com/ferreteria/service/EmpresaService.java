package com.ferreteria.service;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.organizacion.EmpresaRequest;
import com.ferreteria.dto.organizacion.EmpresaResponse;
import com.ferreteria.entity.Empresa;
import com.ferreteria.exception.DuplicadoException;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.repository.EmpresaRepository;
import com.ferreteria.repository.Especificaciones;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empresas (RUC) duenas de las tiendas.
 */
@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final EmpresaRepository empresaRepository;

    @Transactional(readOnly = true)
    public PaginaResponse<EmpresaResponse> listar(String texto, Boolean activo, Pageable pageable) {
        Specification<Empresa> filtro = Specification.allOf(
                Especificaciones.contiene(texto, "ruc", "razonSocial", "nombreComercial"),
                Especificaciones.igual("activo", activo));
        return PaginaResponse.de(empresaRepository.findAll(filtro, pageable), EmpresaResponse::desde);
    }

    @Transactional(readOnly = true)
    public EmpresaResponse obtener(Long id) {
        return EmpresaResponse.desde(buscar(id));
    }

    @Transactional
    public EmpresaResponse crear(EmpresaRequest request) {
        if (empresaRepository.existsByRuc(request.ruc())) {
            throw new DuplicadoException("Ya existe una empresa con el RUC " + request.ruc());
        }
        Empresa empresa = new Empresa();
        copiarDatos(request, empresa);
        return EmpresaResponse.desde(empresaRepository.save(empresa));
    }

    @Transactional
    public EmpresaResponse actualizar(Long id, EmpresaRequest request) {
        Empresa empresa = buscar(id);
        if (empresaRepository.existsByRucAndIdNot(request.ruc(), id)) {
            throw new DuplicadoException("Ya existe una empresa con el RUC " + request.ruc());
        }
        copiarDatos(request, empresa);
        return EmpresaResponse.desde(empresa);
    }

    private void copiarDatos(EmpresaRequest request, Empresa empresa) {
        empresa.setRuc(request.ruc());
        empresa.setRazonSocial(request.razonSocial().trim());
        empresa.setNombreComercial(request.nombreComercial());
        empresa.setDireccion(request.direccion());
        if (request.activo() != null) {
            empresa.setActivo(request.activo());
        }
    }

    private Empresa buscar(Long id) {
        return empresaRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Empresa", id));
    }
}
