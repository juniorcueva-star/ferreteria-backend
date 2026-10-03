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
 * Producto comprado en una compra.
 * Tabla: compra_detalle
 */
@Entity
@Table(name = "compra_detalle")
@Getter
@Setter
@NoArgsConstructor
public class CompraDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id", nullable = false)
    private Compra compra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    // Opcional: si es null la cantidad ya esta en unidad base
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_id")
    private Presentacion presentacion;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidad;

    // Lo que entra al stock
    @Column(name = "cantidad_base", nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidadBase;

    // Costo por unidad base
    @Column(name = "costo_unitario", nullable = false, precision = 12, scale = 4)
    private BigDecimal costoUnitario;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
}
