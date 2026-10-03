package com.ferreteria.service;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.organizacion.UbicacionRequest;
import com.ferreteria.dto.organizacion.UbicacionResponse;
import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.enums.TipoUbicacion;
import com.ferreteria.exception.DuplicadoException;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.EmpresaRepository;
import com.ferreteria.repository.Especificaciones;
import com.ferreteria.repository.UbicacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tiendas y almacen. Los nombres y tipos de ubicaciones no son datos sensibles, por eso todos los
 * usuarios autenticados pueden listarlas (las necesitan para elegir el destino de un traslado, por ejemplo).
 */
@Service
@RequiredArgsConstructor
public class UbicacionService {

    private final UbicacionRepository ubicacionRepository;
    private final EmpresaRepository empresaRepository;

    @Transactional(readOnly = true)
    public PaginaResponse<UbicacionResponse> listar(TipoUbicacion tipo, Long empresaId, Boolean activo,
                                                    Pageable pageable) {
        Specification<Ubicacion> filtro = Specification.allOf(
                Especificaciones.igual("tipo", tipo),
                Especificaciones.igual("empresa.id", empresaId),
                Especificaciones.igual("activo", activo));
        return PaginaResponse.de(ubicacionRepository.findAll(filtro, Ordenamiento.validar(pageable, "id", "nombre", "tipo")), UbicacionResponse::desde);
    }

    @Transactional(readOnly = true)
    public UbicacionResponse obtener(Long id) {
        return UbicacionResponse.desde(buscar(id));
    }

    @Transactional
    public UbicacionResponse crear(UbicacionRequest request) {
        if (ubicacionRepository.existsByNombre(request.nombre().trim())) {
            throw new DuplicadoException("Ya existe una ubicacion llamada " + request.nombre());
        }
        Ubicacion ubicacion = new Ubicacion();
        ubicacion.setTipo(request.tipo());
        copiarDatos(request, ubicacion);
        return UbicacionResponse.desde(ubicacionRepository.save(ubicacion));
    }

    @Transactional
    public UbicacionResponse actualizar(Long id, UbicacionRequest request) {
        Ubicacion ubicacion = buscar(id);
        if (request.tipo() != ubicacion.getTipo()) {
            throw new ReglaNegocioException("No se puede cambiar el tipo de una ubicacion");
        }
        if (ubicacionRepository.existsByNombreAndIdNot(request.nombre().trim(), id)) {
            throw new DuplicadoException("Ya existe una ubicacion llamada " + request.nombre());
        }
        copiarDatos(request, ubicacion);
        return UbicacionResponse.desde(ubicacion);
    }

    private void copiarDatos(UbicacionRequest request, Ubicacion ubicacion) {
        ubicacion.setNombre(request.nombre().trim());
        ubicacion.setDireccion(request.direccion());
        ubicacion.setEmpresa(resolverEmpresa(request));
        if (request.activo() != null) {
            ubicacion.setActivo(request.activo());
        }
    }

    private Empresa resolverEmpresa(UbicacionRequest request) {
        if (request.tipo() == TipoUbicacion.ALMACEN) {
            if (request.empresaId() != null) {
                throw new ReglaNegocioException("El almacen es compartido y no pertenece a una empresa");
            }
            return null;
        }
        if (request.empresaId() == null) {
            throw new ReglaNegocioException("Una tienda debe pertenecer a una empresa (empresaId)");
        }
        return empresaRepository.findById(request.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa", request.empresaId()));
    }

    private Ubicacion buscar(Long id) {
        return ubicacionRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Ubicacion", id));
    }
}
