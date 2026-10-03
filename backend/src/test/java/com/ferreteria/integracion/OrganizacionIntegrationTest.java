package com.ferreteria.integracion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizacionIntegrationTest extends IntegracionTestBase {

    @Test
    @DisplayName("ADMIN crea un vendedor en una tienda y aparece en el listado paginado")
    void adminCreaVendedor() throws Exception {
        String token = datos.token(datos.admin);
        postCon(token, "/api/usuarios", Map.of("nombres", "Ana Perez", "username", "ana", "password", "Clave-Segura-1",
                "rol", "VENDEDOR", "ubicacionId", datos.tienda2.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ubicacionNombre").value("Tienda 2"));

        getCon(token, "/api/usuarios?rol=VENDEDOR&size=500")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.tamano").value(100)); // el tamano maximo se respeta aunque se pida mas
    }

    @Test
    @DisplayName("Un vendedor no puede asignarse a un almacen ni crearse sin ubicacion")
    void vendedorRequiereTienda() throws Exception {
        String token = datos.token(datos.admin);
        Map<String, Object> request = new HashMap<>(Map.of("nombres", "Luis", "username", "luis",
                "password", "Clave-Segura-1", "rol", "VENDEDOR", "ubicacionId", datos.almacen.getId()));
        postCon(token, "/api/usuarios", request)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));

        request.remove("ubicacionId");
        postCon(token, "/api/usuarios", request).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Usuario duplicado responde 409 DUPLICADO")
    void usuarioDuplicado() throws Exception {
        postCon(datos.token(datos.admin), "/api/usuarios", Map.of("nombres", "Otro", "username", "vendedor1",
                "password", "Clave-Segura-1", "rol", "VENDEDOR", "ubicacionId", datos.tienda1.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    @Test
    @DisplayName("El ADMIN no puede desactivarse a si mismo")
    void adminNoSeDesactiva() throws Exception {
        putCon(datos.token(datos.admin), "/api/usuarios/{id}",
                Map.of("nombres", "Admin", "rol", "ADMIN", "activo", false), datos.admin.getId())
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Una tienda necesita empresa y el almacen no puede tenerla")
    void reglasDeUbicacion() throws Exception {
        String token = datos.token(datos.admin);
        postCon(token, "/api/ubicaciones", Map.of("nombre", "Tienda 3", "tipo", "TIENDA"))
                .andExpect(status().isUnprocessableContent());
        postCon(token, "/api/ubicaciones", Map.of("nombre", "Almacen 2", "tipo", "ALMACEN",
                "empresaId", datos.empresa1.getId()))
                .andExpect(status().isUnprocessableContent());
        postCon(token, "/api/ubicaciones", Map.of("nombre", "Tienda 3", "tipo", "TIENDA",
                "empresaId", datos.empresa1.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empresaRuc").value("20111111111"));
    }

    @Test
    @DisplayName("RUC invalido responde 400 VALIDACION")
    void rucInvalido() throws Exception {
        postCon(datos.token(datos.admin), "/api/empresas", Map.of("ruc", "123", "razonSocial", "X"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalle[0]").value("ruc: debe tener 11 digitos"));
    }

    @Test
    @DisplayName("Un vendedor no puede administrar usuarios ni empresas (403)")
    void vendedorSinPermiso() throws Exception {
        String token = datos.token(datos.vendedor1);
        getCon(token, "/api/usuarios")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        getCon(token, "/api/empresas").andExpect(status().isForbidden());
        getCon(token, "/api/ubicaciones").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Contrasena de mas de 72 bytes (limite de BCrypt) responde 400 y no 500")
    void contrasenaMultibyte() throws Exception {
        postCon(datos.token(datos.admin), "/api/usuarios", Map.of("nombres", "Nino", "username", "nino",
                "password", "ñ".repeat(40), "rol", "ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    @DisplayName("Solo se puede ordenar por campos permitidos (no por el hash de la contrasena)")
    void ordenamientoPermitido() throws Exception {
        getCon(datos.token(datos.admin), "/api/usuarios?sort=passwordHash")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        getCon(datos.token(datos.admin), "/api/usuarios?sort=username,desc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].username").value("vendedor2"));
    }
}
