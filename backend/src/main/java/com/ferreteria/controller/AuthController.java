package com.ferreteria.controller;

import com.ferreteria.dto.auth.CambiarPasswordRequest;
import com.ferreteria.dto.auth.LoginRequest;
import com.ferreteria.dto.auth.LoginResponse;
import com.ferreteria.dto.organizacion.UsuarioResponse;
import com.ferreteria.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01. Autenticacion", description = "Login con usuario y contrasena; devuelve el token JWT")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Iniciar sesion", description = "Devuelve el token para el header Authorization: Bearer {token}")
    @SecurityRequirements
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Datos del usuario conectado")
    @GetMapping("/me")
    public UsuarioResponse me() {
        return authService.obtenerUsuarioConectado();
    }

    @Operation(summary = "Cambiar mi contrasena")
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarPassword(@Valid @RequestBody CambiarPasswordRequest request) {
        authService.cambiarPassword(request);
    }
}
