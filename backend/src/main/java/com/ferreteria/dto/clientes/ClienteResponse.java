package com.ferreteria.dto.clientes;

import com.ferreteria.entity.Cliente;
import com.ferreteria.entity.enums.TipoDocumentoIdentidad;

public record ClienteResponse(Long id, String nombre, TipoDocumentoIdentidad tipoDocumento, String numeroDocumento,
                              String telefono, String direccion, boolean activo) {

    public static ClienteResponse desde(Cliente c) {
        return new ClienteResponse(c.getId(), c.getNombre(), c.getTipoDocumento(), c.getNumeroDocumento(),
                c.getTelefono(), c.getDireccion(), c.isActivo());
    }
}
