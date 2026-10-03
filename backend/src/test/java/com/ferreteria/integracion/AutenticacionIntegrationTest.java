package com.ferreteria.integracion;

import com.ferreteria.entity.Usuario;
import com.ferreteria.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AutenticacionIntegrationTest extends IntegracionTestBase {

    @Autowired private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Login correcto devuelve un token que sirve para llamar a la API")
    void loginCorrecto() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "vendedor1", "password", DatosPrueba.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.usuario.rol").value("VENDEDOR"))
                .andExpect(jsonPath("$.usuario.ubicacionNombre").value("Tienda 1"))
                .andExpect(jsonPath("$.usuario.passwordHash").doesNotExist())
                .andReturn();
        String token = leer(resultado, "$.token");

        getCon(token, "/api/auth/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("vendedor1"));
    }

    @Test
    @DisplayName("Contrasena incorrecta o usuario inexistente dan 401 con el mismo mensaje")
    void credencialesInvalidas() throws Exception {
        for (String username : new String[]{"vendedor1", "no_existe"}) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("username", username, "password", "incorrecta"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                    .andExpect(jsonPath("$.mensaje").value("Usuario o contrasena incorrectos"));
        }
    }

    @Test
    @DisplayName("Login sin datos responde 400 con el detalle de cada campo")
    void loginSinDatos() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalle.length()").value(2))
                .andExpect(jsonPath("$.fecha").exists());
    }

    @Test
    @DisplayName("Sin token responde 401 con el formato unico de error")
    void sinToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").exists())
                .andExpect(jsonPath("$.detalle").isArray())
                .andExpect(jsonPath("$.fecha").exists());
    }

    @Test
    @DisplayName("Un token alterado responde 401")
    void tokenAlterado() throws Exception {
        String token = datos.token(datos.vendedor1);
        getCon(token.substring(0, token.length() - 3) + "abc", "/api/auth/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    @DisplayName("Un usuario desactivado pierde el acceso aunque su token no haya vencido")
    void usuarioDesactivado() throws Exception {
        String token = datos.token(datos.vendedor1);
        Usuario vendedor = usuarioRepository.findById(datos.vendedor1.getId()).orElseThrow();
        vendedor.setActivo(false);
        usuarioRepository.save(vendedor);

        getCon(token, "/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cambiar mi contrasena exige la contrasena actual")
    void cambiarPassword() throws Exception {
        String token = datos.token(datos.vendedor1);
        putCon(token, "/api/auth/password", Map.of("passwordActual", "otra", "passwordNueva", "NuevaClave-123"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
        putCon(token, "/api/auth/password",
                Map.of("passwordActual", DatosPrueba.PASSWORD, "passwordNueva", "NuevaClave-123"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "vendedor1", "password", "NuevaClave-123"))))
                .andExpect(status().isOk());
    }
}
