package com.ferreteria.service;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.organizacion.EmpresaRequest;
import com.ferreteria.dto.organizacion.EmpresaResponse;
import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.enums.EntidadAuditada;
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

    private final AuditoriaService auditoria;
    private final EmpresaRepository empresaRepository;

    @Transactional(readOnly = true)
    public PaginaResponse<EmpresaResponse> listar(String texto, Boolean activo, Pageable pageable) {
        Specification<Empresa> filtro = Specification.allOf(
                Especificaciones.contiene(texto, "ruc", "razonSocial", "nombreComercial"),
                Especificaciones.igual("activo", activo));
        return PaginaResponse.de(empresaRepository.findAll(filtro, Ordenamiento.validar(pageable, "id", "ruc", "razonSocial")), EmpresaResponse::desde);
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
        Empresa guardada = empresaRepository.save(empresa);
        auditoria.registrar(EntidadAuditada.EMPRESA, guardada.getId(), "CREAR", "Creó la empresa "
                + guardada.getRazonSocial() + " (RUC " + guardada.getRuc() + ")");
        return EmpresaResponse.desde(guardada);
    }

    @Transactional
    public EmpresaResponse actualizar(Long id, EmpresaRequest request) {
        Empresa empresa = buscar(id);
        if (empresaRepository.existsByRucAndIdNot(request.ruc(), id)) {
            throw new DuplicadoException("Ya existe una empresa con el RUC " + request.ruc());
        }
        AuditoriaService.Cambios cambios = new AuditoriaService.Cambios()
                .campo("RUC", empresa.getRuc(), request.ruc())
                .campo("Razón social", empresa.getRazonSocial(), request.razonSocial())
                .campo("Nombre comercial", empresa.getNombreComercial(), request.nombreComercial())
                .campo("Dirección", empresa.getDireccion(), request.direccion())
                .campo("Teléfono", empresa.getTelefono(), request.telefono())
                .campo("Lema", empresa.getLema(), request.lema())
                .campo("Cuentas bancarias", empresa.getDatosBancarios(), request.datosBancarios())
                .campo("Activa", empresa.isActivo(), request.activo() == null ? empresa.isActivo() : request.activo());
        copiarDatos(request, empresa);
        if (cambios.hay()) {
            auditoria.registrar(EntidadAuditada.EMPRESA, empresa.getId(), "EDITAR", empresa.getRazonSocial() + ": "
                    + cambios);
        }
        return EmpresaResponse.desde(empresa);
    }

    private void copiarDatos(EmpresaRequest request, Empresa empresa) {
        empresa.setRuc(request.ruc());
        empresa.setRazonSocial(request.razonSocial().trim());
        empresa.setNombreComercial(request.nombreComercial());
        empresa.setDireccion(request.direccion());
        empresa.setTelefono(textoOpcional(request.telefono()));
        empresa.setLema(textoOpcional(request.lema()));
        empresa.setDatosBancarios(textoOpcional(request.datosBancarios()));
        if (request.activo() != null) {
            empresa.setActivo(request.activo());
        }
    }

    private Empresa buscar(Long id) {
        return empresaRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Empresa", id));
    }

    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
