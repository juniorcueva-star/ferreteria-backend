package com.ferreteria.service;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.compras.ProveedorRequest;
import com.ferreteria.dto.compras.ProveedorResponse;
import com.ferreteria.entity.Proveedor;
import com.ferreteria.exception.DuplicadoException;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.repository.Especificaciones;
import com.ferreteria.repository.ProveedorRepository;
import com.ferreteria.util.Validar;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;

    @Transactional(readOnly = true)
    public PaginaResponse<ProveedorResponse> listar(String texto, Boolean activo, Pageable pageable) {
        Specification<Proveedor> filtro = Specification.allOf(
                Especificaciones.contiene(texto, "ruc", "razonSocial", "contacto"),
                Especificaciones.igual("activo", activo));
        return PaginaResponse.de(proveedorRepository.findAll(filtro, Ordenamiento.validar(pageable, "id", "ruc", "razonSocial")), ProveedorResponse::desde);
    }

    @Transactional(readOnly = true)
    public ProveedorResponse obtener(Long id) {
        return ProveedorResponse.desde(buscar(id));
    }

    @Transactional
    public ProveedorResponse crear(ProveedorRequest request) {
        String ruc = vacioANull(request.ruc());
        if (ruc != null && proveedorRepository.existsByRuc(ruc)) {
            throw new DuplicadoException("Ya existe un proveedor con el RUC " + ruc);
        }
        Proveedor proveedor = new Proveedor();
        copiarDatos(request, proveedor);
        return ProveedorResponse.desde(proveedorRepository.save(proveedor));
    }

    @Transactional
    public ProveedorResponse actualizar(Long id, ProveedorRequest request) {
        Proveedor proveedor = buscar(id);
        String ruc = vacioANull(request.ruc());
        if (ruc != null && proveedorRepository.existsByRucAndIdNot(ruc, id)) {
            throw new DuplicadoException("Ya existe un proveedor con el RUC " + ruc);
        }
        copiarDatos(request, proveedor);
        return ProveedorResponse.desde(proveedor);
    }

    private void copiarDatos(ProveedorRequest request, Proveedor proveedor) {
        proveedor.setRuc(Validar.ruc(request.ruc(), false));
        proveedor.setRazonSocial(Validar.conLetras(request.razonSocial(), "La razon social"));
        proveedor.setContacto(Validar.nombrePersona(request.contacto(), "El contacto", false));
        proveedor.setTelefono(Validar.telefono(request.telefono(), "Telefono"));
        proveedor.setEmail(vacioANull(request.email()));
        proveedor.setDireccion(vacioANull(request.direccion()));
        if (request.activo() != null) {
            proveedor.setActivo(request.activo());
        }
    }

    private static String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private Proveedor buscar(Long id) {
        return proveedorRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Proveedor", id));
    }
}
