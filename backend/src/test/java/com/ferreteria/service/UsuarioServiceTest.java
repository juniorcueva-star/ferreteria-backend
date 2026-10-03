package com.ferreteria.service;

import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.repository.UbicacionRepository;
import com.ferreteria.repository.UsuarioRepository;
import com.ferreteria.security.AccesoUbicacionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UbicacionRepository ubicacionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AccesoUbicacionService accesoUbicacion;
    @InjectMocks private UsuarioService usuarioService;

    @Test
    @DisplayName("Crea el admin inicial con la contrasena cifrada si no hay ningun ADMIN")
    void creaAdminInicial() {
        when(usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN)).thenReturn(0L);
        when(usuarioRepository.existsByUsername("admin")).thenReturn(false);
        when(passwordEncoder.encode("Secreta-123")).thenReturn("$2a$hash");

        usuarioService.crearAdminInicialSiNoExiste("admin", "Secreta-123");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getRol()).isEqualTo(Rol.ADMIN);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$hash");
        assertThat(captor.getValue().getUbicacion()).isNull();
    }

    @Test
    @DisplayName("No crea otro admin si ya existe uno activo")
    void noDuplicaAdmin() {
        when(usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN)).thenReturn(1L);

        usuarioService.crearAdminInicialSiNoExiste("admin", "Secreta-123");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sin ADMIN_PASSWORD configurada no crea el admin (no hay contrasenas en el codigo)")
    void sinPasswordNoCrea() {
        when(usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN)).thenReturn(0L);

        usuarioService.crearAdminInicialSiNoExiste("admin", "  ");

        verify(usuarioRepository, never()).save(any());
    }
}
