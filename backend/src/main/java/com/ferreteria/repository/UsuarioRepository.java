package com.ferreteria.repository;

import com.ferreteria.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Lo usara el login (Paso 7) para buscar al usuario que intenta entrar
    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);
}
