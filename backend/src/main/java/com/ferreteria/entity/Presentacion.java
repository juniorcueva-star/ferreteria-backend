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
 * Forma de vender un producto con su propio precio. El factor indica cuantas unidades base contiene
 * (Ej: Ciento = 100, Medio kilo = 0.5, Rollo 30 m = 30).
 * Tabla: presentacion
 */
@Entity
@Table(name = "presentacion")
@Getter
@Setter
@NoArgsConstructor
public class Presentacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal factor;

    // Precio con IGV incluido
    @Column(name = "precio_venta", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioVenta;

    @Column(name = "codigo_barras", unique = true, length = 50)
    private String codigoBarras;

    @Column(name = "es_principal", nullable = false)
    private boolean principal;

    @Column(nullable = false)
    private boolean activo = true;
}
