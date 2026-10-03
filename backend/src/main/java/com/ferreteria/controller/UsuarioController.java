package com.ferreteria.controller;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.organizacion.RestablecerPasswordRequest;
import com.ferreteria.dto.organizacion.UsuarioActualizarRequest;
import com.ferreteria.dto.organizacion.UsuarioCrearRequest;
import com.ferreteria.dto.organizacion.UsuarioResponse;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.service.UsuarioService;
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

@Tag(name = "04. Usuarios", description = "Administracion de usuarios y roles. Solo ADMIN")
@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @Operation(summary = "Listar usuarios", description = "Filtros: texto (usuario o nombre), rol, ubicacionId, activo")
    @GetMapping
    public PaginaResponse<UsuarioResponse> listar(@RequestParam(required = false) String texto,
                                                  @RequestParam(required = false) Rol rol,
                                                  @RequestParam(required = false) Long ubicacionId,
                                                  @RequestParam(required = false) Boolean activo,
                                                  @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return usuarioService.listar(texto, rol, ubicacionId, activo, pageable);
    }

    @Operation(summary = "Obtener un usuario")
    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return usuarioService.obtener(id);
    }

    @Operation(summary = "Crear usuario", description = "VENDEDOR necesita una tienda; ALMACENERO, un almacen")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crear(@Valid @RequestBody UsuarioCrearRequest request) {
        return usuarioService.crear(request);
    }

    @Operation(summary = "Actualizar usuario (nombre, rol, ubicacion, activo)")
    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioActualizarRequest request) {
        return usuarioService.actualizar(id, request);
    }

    @Operation(summary = "Restablecer la contrasena de un usuario")
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecerPassword(@PathVariable Long id, @Valid @RequestBody RestablecerPasswordRequest request) {
        usuarioService.restablecerPassword(id, request);
    }
}
