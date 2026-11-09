package com.ferreteria.service;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.organizacion.RestablecerPasswordRequest;
import com.ferreteria.dto.organizacion.UsuarioActualizarRequest;
import com.ferreteria.dto.organizacion.UsuarioCrearRequest;
import com.ferreteria.dto.organizacion.UsuarioResponse;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.entity.enums.TipoUbicacion;
import com.ferreteria.entity.enums.EntidadAuditada;
import com.ferreteria.exception.DuplicadoException;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.Especificaciones;
import com.ferreteria.repository.UbicacionRepository;
import com.ferreteria.repository.UsuarioRepository;
import com.ferreteria.security.AccesoUbicacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administracion de usuarios (solo ADMIN). Las contrasenas se guardan con BCrypt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final AuditoriaService auditoria;
    private final UsuarioRepository usuarioRepository;
    private final UbicacionRepository ubicacionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccesoUbicacionService accesoUbicacion;

    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioResponse> listar(String texto, Rol rol, Long ubicacionId, Boolean activo,
                                                  Pageable pageable) {
        Specification<Usuario> filtro = Specification.allOf(
                Especificaciones.contiene(texto, "username", "nombres"),
                Especificaciones.igual("rol", rol),
                Especificaciones.igual("ubicacion.id", ubicacionId),
                Especificaciones.igual("activo", activo));
        return PaginaResponse.de(usuarioRepository.findAll(filtro, Ordenamiento.validar(pageable, "id", "username", "nombres", "rol")), UsuarioResponse::desde);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id) {
        return UsuarioResponse.desde(buscar(id));
    }

    @Transactional
    public UsuarioResponse crear(UsuarioCrearRequest request) {
        if (usuarioRepository.existsByUsername(request.username())) {
            throw new DuplicadoException("Ya existe el usuario " + request.username());
        }
        Usuario usuario = new Usuario();
        usuario.setUsername(request.username());
        usuario.setNombres(request.nombres().trim());
        usuario.setRol(request.rol());
        usuario.setUbicacion(resolverUbicacion(request.rol(), request.ubicacionId()));
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        Usuario guardado = usuarioRepository.save(usuario);
        auditoria.registrar(EntidadAuditada.USUARIO, guardado.getId(), "CREAR", "Creó el usuario "
                + guardado.getUsername() + " (" + guardado.getRol() + (guardado.getUbicacion() == null ? ""
                : ", " + guardado.getUbicacion().getNombre()) + ")");
        return UsuarioResponse.desde(guardado);
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioActualizarRequest request) {
        Usuario usuario = buscar(id);
        boolean dejaDeSerAdminActivo = usuario.getRol() == Rol.ADMIN && usuario.isActivo()
                && (request.rol() != Rol.ADMIN || !request.activo());
        if (dejaDeSerAdminActivo) {
            if (usuario.getId().equals(accesoUbicacion.usuarioActual().id())) {
                throw new ReglaNegocioException("No puede quitarse a si mismo el rol ADMIN ni desactivarse");
            }
            if (usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN) <= 1) {
                throw new ReglaNegocioException("Debe quedar al menos un ADMIN activo");
            }
        }
        Ubicacion ubicacion = resolverUbicacion(request.rol(), request.ubicacionId());
        AuditoriaService.Cambios cambios = new AuditoriaService.Cambios()
                .campo("Nombres", usuario.getNombres(), request.nombres().trim())
                .campo("Rol", usuario.getRol(), request.rol())
                .campo("Ubicación", usuario.getUbicacion() == null ? null : usuario.getUbicacion().getNombre(),
                        ubicacion == null ? null : ubicacion.getNombre())
                .campo("Activo", usuario.isActivo(), request.activo());
        usuario.setNombres(request.nombres().trim());
        usuario.setRol(request.rol());
        usuario.setUbicacion(ubicacion);
        usuario.setActivo(request.activo());
        if (cambios.hay()) {
            auditoria.registrar(EntidadAuditada.USUARIO, usuario.getId(), "EDITAR", "Usuario "
                    + usuario.getUsername() + ": " + cambios);
        }
        return UsuarioResponse.desde(usuario);
    }

    @Transactional
    public void restablecerPassword(Long id, RestablecerPasswordRequest request) {
        Usuario usuario = buscar(id);
        usuario.setPasswordHash(passwordEncoder.encode(request.passwordNueva()));
        auditoria.registrar(EntidadAuditada.USUARIO, usuario.getId(), "CONTRASENA", "Restableció la contraseña de "
                + usuario.getUsername());
    }

    /**
     * Crea el primer ADMIN al arrancar si no hay ninguno activo. Usa ADMIN_USERNAME y ADMIN_PASSWORD.
     */
    @Transactional
    public void crearAdminInicialSiNoExiste(String username, String password) {
        if (usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN) > 0) {
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("No hay ningun ADMIN activo y no se configuro ADMIN_PASSWORD: no se crea el admin inicial");
            return;
        }
        if (usuarioRepository.existsByUsername(username)) {
            log.warn("Ya existe el usuario '{}' pero no es ADMIN activo: no se crea el admin inicial", username);
            return;
        }
        Usuario admin = new Usuario();
        admin.setUsername(username);
        admin.setNombres("Administrador");
        admin.setRol(Rol.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(password));
        usuarioRepository.save(admin);
        log.info("Usuario ADMIN inicial '{}' creado", username);
    }

    /** VENDEDOR -> una tienda; ALMACENERO -> un almacen; ADMIN -> sin ubicacion. */
    private Ubicacion resolverUbicacion(Rol rol, Long ubicacionId) {
        if (rol == Rol.ADMIN) {
            return null;
        }
        if (ubicacionId == null) {
            throw new ReglaNegocioException("El rol " + rol + " necesita una ubicacion (ubicacionId)");
        }
        Ubicacion ubicacion = ubicacionRepository.findById(ubicacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ubicacion", ubicacionId));
        TipoUbicacion esperado = rol == Rol.VENDEDOR ? TipoUbicacion.TIENDA : TipoUbicacion.ALMACEN;
        if (ubicacion.getTipo() != esperado) {
            throw new ReglaNegocioException("El rol " + rol + " debe asignarse a una ubicacion de tipo " + esperado);
        }
        return ubicacion;
    }

    private Usuario buscar(Long id) {
        return usuarioRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    }
}
