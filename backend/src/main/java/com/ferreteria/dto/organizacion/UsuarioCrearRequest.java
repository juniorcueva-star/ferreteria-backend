package com.ferreteria.dto.organizacion;

import com.ferreteria.entity.enums.Rol;
import com.ferreteria.dto.comun.Contrasena;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * VENDEDOR necesita una tienda y ALMACENERO un almacen (ubicacionId). El ADMIN no lleva ubicacion.
 */
public record UsuarioCrearRequest(
        @NotBlank @Size(max = 120) String nombres,
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "[a-zA-Z0-9._-]+", message = "solo letras, numeros, punto, guion y guion bajo")
        String username,
        @NotBlank @Contrasena String password,
        @NotNull Rol rol,
        Long ubicacionId) {
}
