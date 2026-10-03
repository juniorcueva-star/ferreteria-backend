package com.ferreteria.controller;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.compras.ProveedorRequest;
import com.ferreteria.dto.compras.ProveedorResponse;
import com.ferreteria.service.ProveedorService;
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

@Tag(name = "07. Proveedores", description = "ADMIN y ALMACENERO")
@RestController
@RequestMapping("/api/proveedores")
@RequiredArgsConstructor
public class ProveedorController {

    private final ProveedorService proveedorService;

    @Operation(summary = "Listar proveedores", description = "Filtros: texto (RUC, razon social o contacto) y activo")
    @GetMapping
    public PaginaResponse<ProveedorResponse> listar(@RequestParam(required = false) String texto,
                                                    @RequestParam(required = false) Boolean activo,
                                                    @ParameterObject @PageableDefault(size = 20, sort = "razonSocial") Pageable pageable) {
        return proveedorService.listar(texto, activo, pageable);
    }

    @Operation(summary = "Obtener un proveedor")
    @GetMapping("/{id}")
    public ProveedorResponse obtener(@PathVariable Long id) {
        return proveedorService.obtener(id);
    }

    @Operation(summary = "Crear proveedor")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProveedorResponse crear(@Valid @RequestBody ProveedorRequest request) {
        return proveedorService.crear(request);
    }

    @Operation(summary = "Actualizar proveedor")
    @PutMapping("/{id}")
    public ProveedorResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProveedorRequest request) {
        return proveedorService.actualizar(id, request);
    }
}
