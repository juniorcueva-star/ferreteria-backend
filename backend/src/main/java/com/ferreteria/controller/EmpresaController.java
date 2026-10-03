package com.ferreteria.controller;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.organizacion.EmpresaRequest;
import com.ferreteria.dto.organizacion.EmpresaResponse;
import com.ferreteria.service.EmpresaService;
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

@Tag(name = "02. Empresas", description = "Empresas (RUC) duenas de las tiendas. Solo ADMIN")
@RestController
@RequestMapping("/api/empresas")
@RequiredArgsConstructor
public class EmpresaController {

    private final EmpresaService empresaService;

    @Operation(summary = "Listar empresas", description = "Filtros: texto (RUC o razon social) y activo")
    @GetMapping
    public PaginaResponse<EmpresaResponse> listar(@RequestParam(required = false) String texto,
                                                  @RequestParam(required = false) Boolean activo,
                                                  @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return empresaService.listar(texto, activo, pageable);
    }

    @Operation(summary = "Obtener una empresa")
    @GetMapping("/{id}")
    public EmpresaResponse obtener(@PathVariable Long id) {
        return empresaService.obtener(id);
    }

    @Operation(summary = "Crear empresa")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmpresaResponse crear(@Valid @RequestBody EmpresaRequest request) {
        return empresaService.crear(request);
    }

    @Operation(summary = "Actualizar empresa")
    @PutMapping("/{id}")
    public EmpresaResponse actualizar(@PathVariable Long id, @Valid @RequestBody EmpresaRequest request) {
        return empresaService.actualizar(id, request);
    }
}
