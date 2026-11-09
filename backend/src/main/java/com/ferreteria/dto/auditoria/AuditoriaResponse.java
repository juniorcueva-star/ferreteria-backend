package com.ferreteria.dto.auditoria;

import com.ferreteria.entity.Auditoria;
import com.ferreteria.entity.enums.EntidadAuditada;

import java.time.OffsetDateTime;

public record AuditoriaResponse(Long id, OffsetDateTime fecha, String usuario, String usuarioNombre,
                                EntidadAuditada entidad, Long entidadId, String accion, String descripcion) {

    public static AuditoriaResponse desde(Auditoria a) {
        return new AuditoriaResponse(a.getId(), a.getFecha(),
                a.getUsuario() == null ? null : a.getUsuario().getUsername(),
                a.getUsuario() == null ? null : a.getUsuario().getNombres(),
                a.getEntidad(), a.getEntidadId(), a.getAccion(), a.getDescripcion());
    }
}
