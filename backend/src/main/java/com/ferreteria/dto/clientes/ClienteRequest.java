package com.ferreteria.dto.clientes;

import com.ferreteria.entity.enums.TipoDocumentoIdentidad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cliente. Documento: NINGUNO (sin numero), DNI (8 digitos), RUC (11 digitos) o CE (carne de extranjeria).
 */
public record ClienteRequest(
        @NotBlank @Size(max = 150) String nombre,
        TipoDocumentoIdentidad tipoDocumento,
        @Size(max = 15) String numeroDocumento,
        @Size(max = 20) String telefono,
        @Size(max = 200) String direccion,
        Boolean activo) {
}
