package com.ferreteria.service;

import com.ferreteria.dto.auth.CambiarPasswordRequest;
import com.ferreteria.dto.auth.LoginRequest;
import com.ferreteria.dto.auth.LoginResponse;
import com.ferreteria.dto.organizacion.UsuarioResponse;
import com.ferreteria.entity.Usuario;
import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.UsuarioRepository;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicio de sesion y datos del usuario conectado.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String CREDENCIALES_INVALIDAS = "Usuario o contrasena incorrectos";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AccesoUbicacionService accesoUbicacion;

    // Hash de relleno: si el usuario no existe igual se compara una contrasena, para que el tiempo de
    // respuesta no revele que usuarios existen.
    private String hashRelleno;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByUsername(request.username()).orElse(null);
        if (usuario == null) {
            passwordEncoder.matches(request.password(), hashRelleno());
            throw new ApiException(CodigoError.CREDENCIALES_INVALIDAS, CREDENCIALES_INVALIDAS);
        }
        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash()) || !usuario.isActivo()) {
            throw new ApiException(CodigoError.CREDENCIALES_INVALIDAS, CREDENCIALES_INVALIDAS);
        }
        JwtService.TokenGenerado token = jwtService.generar(usuario);
        return new LoginResponse(token.token(), "Bearer", token.expiraEn(), UsuarioResponse.desde(usuario));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtenerUsuarioConectado() {
        return UsuarioResponse.desde(buscarUsuarioConectado());
    }

    @Transactional
    public void cambiarPassword(CambiarPasswordRequest request) {
        Usuario usuario = buscarUsuarioConectado();
        if (!passwordEncoder.matches(request.passwordActual(), usuario.getPasswordHash())) {
            throw new ReglaNegocioException("La contrasena actual no es correcta");
        }
        usuario.setPasswordHash(passwordEncoder.encode(request.passwordNueva()));
    }

    private Usuario buscarUsuarioConectado() {
        Long id = accesoUbicacion.usuarioActual().id();
        return usuarioRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    }

    private String hashRelleno() {
        if (hashRelleno == null) {
            hashRelleno = passwordEncoder.encode("relleno-para-tiempo-constante");
        }
        return hashRelleno;
    }
}
