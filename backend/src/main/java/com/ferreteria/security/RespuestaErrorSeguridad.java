package com.ferreteria.security;

import com.ferreteria.dto.comun.ErrorResponse;
import com.ferreteria.exception.CodigoError;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * Responde los 401 y 403 que genera el filtro de seguridad (antes de llegar a un controller)
 * con el mismo formato de error que el resto de la API.
 */
@Component
@RequiredArgsConstructor
public class RespuestaErrorSeguridad {

    private final ObjectMapper objectMapper;

    public AuthenticationEntryPoint noAutenticado() {
        return (request, response, ex) -> escribir(response, CodigoError.NO_AUTENTICADO,
                "Debe iniciar sesion con un token valido");
    }

    public AccessDeniedHandler accesoDenegado() {
        return (request, response, ex) -> escribir(response, CodigoError.ACCESO_DENEGADO,
                "No tiene permiso para realizar esta operacion");
    }

    private void escribir(HttpServletResponse response, CodigoError codigo, String mensaje) throws IOException {
        response.setStatus(codigo.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.de(codigo.name(), mensaje, List.of()));
    }
}
