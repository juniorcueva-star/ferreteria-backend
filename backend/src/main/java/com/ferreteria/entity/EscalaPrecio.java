package com.ferreteria.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Precio por mayor de una presentacion: desde cierta cantidad (en la presentacion) cada una cuesta menos.
 * Ej: Unidad a S/ 0.50; desde 100 a S/ 0.40.
 * Tabla: escala_precio
 */
@Entity
@Table(name = "escala_precio")
@Getter
@Setter
@NoArgsConstructor
public class EscalaPrecio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentacion_id", nullable = false)
    private Presentacion presentacion;

    @Column(name = "cantidad_minima", nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidadMinima;

    // Precio por presentacion con IGV
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;
}
