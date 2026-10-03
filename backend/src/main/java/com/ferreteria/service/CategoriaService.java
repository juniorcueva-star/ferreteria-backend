package com.ferreteria.service;

import com.ferreteria.dto.catalogo.CategoriaRequest;
import com.ferreteria.dto.catalogo.CategoriaResponse;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.entity.Categoria;
import com.ferreteria.exception.DuplicadoException;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.repository.CategoriaRepository;
import com.ferreteria.repository.Especificaciones;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public PaginaResponse<CategoriaResponse> listar(String texto, Boolean activo, Pageable pageable) {
        Specification<Categoria> filtro = Specification.allOf(
                Especificaciones.contiene(texto, "nombre"),
                Especificaciones.igual("activo", activo));
        return PaginaResponse.de(categoriaRepository.findAll(filtro, pageable), CategoriaResponse::desde);
    }

    @Transactional
    public CategoriaResponse crear(CategoriaRequest request) {
        String nombre = request.nombre().trim();
        if (categoriaRepository.existsByNombreIgnoreCase(nombre)) {
            throw new DuplicadoException("Ya existe la categoria " + nombre);
        }
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        if (request.activo() != null) {
            categoria.setActivo(request.activo());
        }
        return CategoriaResponse.desde(categoriaRepository.save(categoria));
    }

    @Transactional
    public CategoriaResponse actualizar(Long id, CategoriaRequest request) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoria", id));
        String nombre = request.nombre().trim();
        if (categoriaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new DuplicadoException("Ya existe la categoria " + nombre);
        }
        categoria.setNombre(nombre);
        if (request.activo() != null) {
            categoria.setActivo(request.activo());
        }
        return CategoriaResponse.desde(categoria);
    }
}
