package com.ferreteria.dto.organizacion;

import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.enums.TipoUbicacion;

public record UbicacionResponse(Long id, String nombre, TipoUbicacion tipo, Long empresaId, String empresaRuc,
                                String empresaRazonSocial, String direccion, boolean activo) {

    public static UbicacionResponse desde(Ubicacion u) {
        Empresa empresa = u.getEmpresa();
        return new UbicacionResponse(u.getId(), u.getNombre(), u.getTipo(),
                empresa == null ? null : empresa.getId(),
                empresa == null ? null : empresa.getRuc(),
                empresa == null ? null : empresa.getRazonSocial(),
                u.getDireccion(), u.isActivo());
    }
}
