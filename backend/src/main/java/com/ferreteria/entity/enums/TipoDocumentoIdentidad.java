package com.ferreteria.entity.enums;

/**
 * Documento de identidad de un cliente.
 * Debe coincidir con el CHECK ck_cliente_tipo_doc.
 */
public enum TipoDocumentoIdentidad {
    NINGUNO,
    DNI,
    RUC,
    CE
}
