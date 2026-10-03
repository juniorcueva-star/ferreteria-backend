package com.ferreteria.security;

import com.ferreteria.entity.enums.Rol;
import com.ferreteria.exception.AccesoDenegadoException;
import com.ferreteria.exception.ReglaNegocioException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccesoUbicacionServiceTest {

    private final AccesoUbicacionService acceso = new AccesoUbicacionService();

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("El vendedor solo consulta su tienda: sin filtro se usa la suya y otra da 403")
    void vendedorSoloSuTienda() {
        autenticar(new UsuarioActual(2L, "vendedor1", "V", Rol.VENDEDOR, 10L));

        assertThat(acceso.ubicacionParaConsultar(null)).isEqualTo(10L);
        assertThat(acceso.ubicacionParaConsultar(10L)).isEqualTo(10L);
        assertThatThrownBy(() -> acceso.ubicacionParaConsultar(20L)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> acceso.validarAcceso(20L)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> acceso.ubicacionParaOperar(20L)).isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    @DisplayName("El ADMIN consulta cualquier ubicacion (o todas) pero debe indicar donde opera")
    void adminVeTodo() {
        autenticar(new UsuarioActual(1L, "admin", "A", Rol.ADMIN, null));

        assertThat(acceso.ubicacionParaConsultar(null)).isNull();
        assertThat(acceso.ubicacionParaConsultar(20L)).isEqualTo(20L);
        acceso.validarAcceso(20L);
        assertThat(acceso.ubicacionParaOperar(20L)).isEqualTo(20L);
        assertThatThrownBy(() -> acceso.ubicacionParaOperar(null)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("Sin usuario autenticado se niega el acceso")
    void sinUsuario() {
        assertThatThrownBy(acceso::usuarioActual).isInstanceOf(AccesoDenegadoException.class);
    }

    private void autenticar(UsuarioActual usuario) {
        SecurityContextHolder.getContext().setAuthentication(new UsuarioAuthenticationToken(usuario));
    }
}
