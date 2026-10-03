package com.ferreteria.dto.compras;

import com.ferreteria.entity.Proveedor;

public record ProveedorResponse(Long id, String ruc, String razonSocial, String contacto, String telefono,
                                String email, String direccion, boolean activo) {

    public static ProveedorResponse desde(Proveedor p) {
        return new ProveedorResponse(p.getId(), p.getRuc(), p.getRazonSocial(), p.getContacto(), p.getTelefono(),
                p.getEmail(), p.getDireccion(), p.isActivo());
    }
}
