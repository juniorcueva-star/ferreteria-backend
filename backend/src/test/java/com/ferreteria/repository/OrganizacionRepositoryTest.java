package com.ferreteria.repository;

import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.entity.enums.TipoUbicacion;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba los repositorios de empresa, ubicacion y usuario contra PostgreSQL real (esquema de pruebas).
 * Cada test corre en una transaccion que se deshace al final (rollback): no deja datos.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // usar PostgreSQL real, no una BD en memoria
@ActiveProfiles("test")
class OrganizacionRepositoryTest {

    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private UbicacionRepository ubicacionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Guarda empresa, tienda y vendedor, y los recupera con sus relaciones")
    void guardaYRecuperaConRelaciones() {
        Empresa empresa = empresaRepository.save(nuevaEmpresa("20999999991"));
        Ubicacion tienda = ubicacionRepository.save(nuevaUbicacion("Tienda Test", TipoUbicacion.TIENDA, empresa));
        usuarioRepository.save(nuevoUsuario("vendedor_test", Rol.VENDEDOR, tienda));

        // Obliga a hacer los INSERT y limpia la memoria de JPA,
        // para que la busqueda de abajo lea de verdad desde PostgreSQL
        entityManager.flush();
        entityManager.clear();

        Usuario encontrado = usuarioRepository.findByUsername("vendedor_test").orElseThrow();
        assertThat(encontrado.getRol()).isEqualTo(Rol.VENDEDOR);
        assertThat(encontrado.getCreatedAt()).isNotNull();
        assertThat(encontrado.getUbicacion().getNombre()).isEqualTo("Tienda Test");
        assertThat(encontrado.getUbicacion().getEmpresa().getRuc()).isEqualTo("20999999991");
        assertThat(empresaRepository.existsByRuc("20999999991")).isTrue();
    }

    @Test
    @DisplayName("El almacen se puede guardar sin empresa (es compartido)")
    void almacenSinEmpresa() {
        ubicacionRepository.saveAndFlush(nuevaUbicacion("Almacen Test", TipoUbicacion.ALMACEN, null));

        assertThat(ubicacionRepository.findByTipoAndActivoTrue(TipoUbicacion.ALMACEN))
                .extracting(Ubicacion::getNombre)
                .contains("Almacen Test");
    }

    @Test
    @DisplayName("La BD rechaza una tienda sin empresa (CHECK ck_tienda_con_empresa)")
    void tiendaSinEmpresaEsRechazada() {
        Ubicacion tiendaSinEmpresa = nuevaUbicacion("Tienda Sin RUC", TipoUbicacion.TIENDA, null);

        assertThatThrownBy(() -> ubicacionRepository.saveAndFlush(tiendaSinEmpresa))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La BD rechaza un vendedor sin tienda (CHECK ck_usuario_ubicacion)")
    void vendedorSinUbicacionEsRechazado() {
        Usuario vendedorSinTienda = nuevoUsuario("vendedor_sin_tienda", Rol.VENDEDOR, null);

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(vendedorSinTienda))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- datos de prueba ----------

    private Empresa nuevaEmpresa(String ruc) {
        Empresa empresa = new Empresa();
        empresa.setRuc(ruc);
        empresa.setRazonSocial("Empresa de Prueba S.A.C.");
        return empresa;
    }

    private Ubicacion nuevaUbicacion(String nombre, TipoUbicacion tipo, Empresa empresa) {
        Ubicacion ubicacion = new Ubicacion();
        ubicacion.setNombre(nombre);
        ubicacion.setTipo(tipo);
        ubicacion.setEmpresa(empresa);
        return ubicacion;
    }

    private Usuario nuevoUsuario(String username, Rol rol, Ubicacion ubicacion) {
        Usuario usuario = new Usuario();
        usuario.setNombres("Usuario de Prueba");
        usuario.setUsername(username);
        usuario.setPasswordHash("hash-de-prueba");
        usuario.setRol(rol);
        usuario.setUbicacion(ubicacion);
        return usuario;
    }
}
