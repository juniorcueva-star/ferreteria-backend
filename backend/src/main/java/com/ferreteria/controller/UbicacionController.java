package com.ferreteria.controller;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.organizacion.UbicacionRequest;
import com.ferreteria.dto.organizacion.UbicacionResponse;
import com.ferreteria.entity.enums.TipoUbicacion;
import com.ferreteria.service.UbicacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "03. Ubicaciones", description = "Almacen y tiendas. Listar: todos; crear y editar: ADMIN")
@RestController
@RequestMapping("/api/ubicaciones")
@RequiredArgsConstructor
public class UbicacionController {

    private final UbicacionService ubicacionService;

    @Operation(summary = "Listar ubicaciones", description = "Filtros: tipo (ALMACEN o TIENDA), empresaId y activo")
    @GetMapping
    public PaginaResponse<UbicacionResponse> listar(@RequestParam(required = false) TipoUbicacion tipo,
                                                    @RequestParam(required = false) Long empresaId,
                                                    @RequestParam(required = false) Boolean activo,
                                                    @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return ubicacionService.listar(tipo, empresaId, activo, pageable);
    }

    @Operation(summary = "Obtener una ubicacion")
    @GetMapping("/{id}")
    public UbicacionResponse obtener(@PathVariable Long id) {
        return ubicacionService.obtener(id);
    }

    @Operation(summary = "Crear ubicacion", description = "Una TIENDA necesita empresaId; el ALMACEN no")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UbicacionResponse crear(@Valid @RequestBody UbicacionRequest request) {
        return ubicacionService.crear(request);
    }

    @Operation(summary = "Actualizar ubicacion", description = "El tipo no se puede cambiar")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UbicacionResponse actualizar(@PathVariable Long id, @Valid @RequestBody UbicacionRequest request) {
        return ubicacionService.actualizar(id, request);
    }
}
