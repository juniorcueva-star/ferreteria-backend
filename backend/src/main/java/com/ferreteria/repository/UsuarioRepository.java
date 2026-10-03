package com.ferreteria.repository;

import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.security.UsuarioActual;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    // Lo usa el login para buscar al usuario que intenta entrar
    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRolAndActivoTrue(Rol rol);

    // Lo usa la seguridad en cada peticion: solo devuelve usuarios activos
    @Query("""
            select new com.ferreteria.security.UsuarioActual(u.id, u.username, u.nombres, u.rol, ub.id)
            from Usuario u left join u.ubicacion ub
            where u.id = :id and u.activo = true
            """)
    Optional<UsuarioActual> buscarUsuarioActivo(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = "ubicacion")
    Page<Usuario> findAll(Specification<Usuario> spec, Pageable pageable);
}
