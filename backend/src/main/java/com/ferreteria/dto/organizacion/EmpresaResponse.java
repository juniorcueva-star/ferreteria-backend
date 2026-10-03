package com.ferreteria.dto.organizacion;

import com.ferreteria.entity.Empresa;

public record EmpresaResponse(Long id, String ruc, String razonSocial, String nombreComercial, String direccion,
                              boolean activo) {

    public static EmpresaResponse desde(Empresa e) {
        return new EmpresaResponse(e.getId(), e.getRuc(), e.getRazonSocial(), e.getNombreComercial(),
                e.getDireccion(), e.isActivo());
    }
}
