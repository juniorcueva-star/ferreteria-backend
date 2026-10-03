package com.ferreteria.dto.auth;

import com.ferreteria.dto.organizacion.UsuarioResponse;

import java.time.Instant;

/**
 * Token para enviar en cada peticion en el header "Authorization: Bearer {token}".
 */
public record LoginResponse(String token, String tipo, Instant expiraEn, UsuarioResponse usuario) {
}
