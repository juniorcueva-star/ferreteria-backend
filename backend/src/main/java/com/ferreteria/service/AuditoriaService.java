package com.ferreteria.service;

import com.ferreteria.dto.auditoria.AuditoriaResponse;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.entity.Auditoria;
import com.ferreteria.entity.enums.EntidadAuditada;
import com.ferreteria.repository.AuditoriaRepository;
import com.ferreteria.repository.Especificaciones;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Auditoria de cambios (pedido del dueno): se registra dentro de la misma transaccion que el cambio, asi que si el
 * cambio se deshace, su registro tambien. Solo el ADMIN consulta la auditoria.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private static final int LARGO_MAXIMO = 500;

    private final AuditoriaRepository auditoriaRepository;
    private final Buscador buscador;
    private final Calendario calendario;

    /** Registra un cambio hecho por el usuario actual. Debe llamarse dentro de la transaccion del cambio. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(EntidadAuditada entidad, Long entidadId, String accion, String descripcion) {
        Auditoria auditoria = new Auditoria();
        auditoria.setUsuario(buscador.usuarioActual());
        auditoria.setEntidad(entidad);
        auditoria.setEntidadId(entidadId);
        auditoria.setAccion(accion);
        auditoria.setDescripcion(descripcion.length() <= LARGO_MAXIMO ? descripcion
                : descripcion.substring(0, LARGO_MAXIMO - 3) + "...");
        auditoriaRepository.save(auditoria);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AuditoriaResponse> listar(EntidadAuditada entidad, Long entidadId, Long usuarioId,
                                                    String texto, LocalDate desde, LocalDate hasta,
                                                    Pageable pageable) {
        calendario.validarRango(desde, hasta);
        Specification<Auditoria> filtro = Specification.allOf(
                Especificaciones.igual("entidad", entidad),
                Especificaciones.igual("entidadId", entidadId),
                Especificaciones.igual("usuario.id", usuarioId),
                Especificaciones.contiene(texto, "descripcion"),
                Especificaciones.desde("fecha", calendario.inicio(desde)),
                Especificaciones.antesDe("fecha", calendario.finExclusivo(hasta)));
        return PaginaResponse.de(auditoriaRepository.findAll(filtro,
                Ordenamiento.validar(pageable, "id", "fecha")), AuditoriaResponse::desde);
    }

    /** Lista de cambios "campo: antes -> despues" (solo los campos que cambiaron). */
    public static final class Cambios {

        private final List<String> lista = new ArrayList<>();

        public Cambios campo(String nombre, Object antes, Object despues) {
            String a = texto(antes);
            String d = texto(despues);
            if (!Objects.equals(a, d)) {
                lista.add(nombre + ": " + (a == null ? "(vacío)" : a) + " → " + (d == null ? "(vacío)" : d));
            }
            return this;
        }

        public boolean hay() {
            return !lista.isEmpty();
        }

        @Override
        public String toString() {
            return String.join("; ", lista);
        }

        private static String texto(Object valor) {
            if (valor == null) {
                return null;
            }
            if (valor instanceof Boolean b) {
                return b ? "sí" : "no";
            }
            if (valor instanceof BigDecimal n) {
                return n.stripTrailingZeros().toPlainString();
            }
            String texto = valor.toString().trim();
            return texto.isEmpty() ? null : texto;
        }
    }

    public static String soles(BigDecimal monto) {
        return String.format(Locale.US, "S/ %,.2f", monto);
    }
}
