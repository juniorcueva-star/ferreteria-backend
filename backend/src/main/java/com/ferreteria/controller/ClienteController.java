package com.ferreteria.controller;

import com.ferreteria.dto.clientes.ClienteRequest;
import com.ferreteria.dto.clientes.ClienteResponse;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.service.ClienteService;
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

@Tag(name = "11. Clientes", description = "Clientes para ventas al fiado. ADMIN y VENDEDOR")
@RestController
@RequestMapping("/api/clientes")
@PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @Operation(summary = "Listar clientes", description = "Filtros: texto (nombre, documento o telefono) y activo")
    @GetMapping
    public PaginaResponse<ClienteResponse> listar(@RequestParam(required = false) String texto,
                                                  @RequestParam(required = false) Boolean activo,
                                                  @ParameterObject @PageableDefault(sort = "nombre") Pageable pageable) {
        return clienteService.listar(texto, activo, pageable);
    }

    @Operation(summary = "Obtener un cliente")
    @GetMapping("/{id}")
    public ClienteResponse obtener(@PathVariable Long id) {
        return clienteService.obtener(id);
    }

    @Operation(summary = "Registrar cliente")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteResponse crear(@Valid @RequestBody ClienteRequest request) {
        return clienteService.crear(request);
    }

    @Operation(summary = "Actualizar cliente")
    @PutMapping("/{id}")
    public ClienteResponse actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
        return clienteService.actualizar(id, request);
    }
}
