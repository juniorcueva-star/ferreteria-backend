package com.ferreteria.entity.enums;

/**
 * Rol del usuario. Debe coincidir con el CHECK ck_usuario_rol de la tabla usuario.
 * <ul>
 *   <li>ADMIN: el dueno, ve todo.</li>
 *   <li>VENDEDOR: vende solo en su tienda.</li>
 *   <li>ALMACENERO: recibe compras y envia traslados desde el almacen.</li>
 * </ul>
 */
public enum Rol {
    ADMIN,
    VENDEDOR,
    ALMACENERO
}
