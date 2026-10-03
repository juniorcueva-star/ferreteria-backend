package com.ferreteria.controller;

import com.ferreteria.dto.catalogo.CategoriaRequest;
import com.ferreteria.dto.catalogo.CategoriaResponse;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.service.CategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "05. Categorias", description = "Listar: todos; crear y editar: ADMIN")
@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @Operation(summary = "Listar categorias")
    @GetMapping
    public PaginaResponse<CategoriaResponse> listar(@RequestParam(required = false) String texto,
                                                    @RequestParam(required = false) Boolean activo,
                                                    @ParameterObject @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {
        return categoriaService.listar(texto, activo, pageable);
    }

    @Operation(summary = "Crear categoria")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse crear(@Valid @RequestBody CategoriaRequest request) {
        return categoriaService.crear(request);
    }

    @Operation(summary = "Actualizar categoria")
    @PutMapping("/{id}")
    public CategoriaResponse actualizar(@PathVariable Long id, @Valid @RequestBody CategoriaRequest request) {
        return categoriaService.actualizar(id, request);
    }
}
