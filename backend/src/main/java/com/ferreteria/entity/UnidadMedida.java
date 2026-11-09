package com.ferreteria.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Unidad de venta del catalogo (docena, ciento, millar, caja, kilo...).
 * Tabla: unidad_medida
 */
@Entity
@Table(name = "unidad_medida")
@Getter
@Setter
@NoArgsConstructor
public class UnidadMedida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(nullable = false, length = 60)
    private String nombre;

    // Decimales que acepta una presentacion de esta unidad (0 = solo enteros)
    @Column(nullable = false)
    private int decimales;

    // Unidades base que suele contener (Ej: docena = 12); null si depende del producto
    @Column(name = "factor_sugerido", precision = 14, scale = 3)
    private BigDecimal factorSugerido;

    @Column(nullable = false)
    private boolean activo = true;
}
