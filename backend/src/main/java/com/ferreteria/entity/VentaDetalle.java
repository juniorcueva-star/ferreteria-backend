package com.ferreteria.entity;

import com.ferreteria.entity.enums.TipoPrecio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Producto vendido en una venta. Guarda una copia del nombre, factor y precio del momento de la venta.
 * Tabla: venta_detalle
 */
@Entity
@Table(name = "venta_detalle")
@Getter
@Setter
@NoArgsConstructor
public class VentaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    /** Null en las lineas de completo */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_id")
    private Presentacion presentacion;

    /** Completo vendido (Ej: perno + tuerca + arandela); null en las lineas de presentacion */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completo_id")
    private Completo completo;

    /** Piezas del completo al momento del documento, Ej: "1 Perno (Unidad) + 1 Tuerca (Unidad)" */
    @Column(length = 300)
    private String composicion;

    @Column(nullable = false, length = 200)
    private String descripcion;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidad;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal factor;

    // Lo que se descuenta del stock: ROUND(cantidad * factor, 3)
    @Column(name = "cantidad_base", nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidadBase;

    // Precio de la presentacion con IGV
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    // Lista, por mayor u oferta: se imprime en el documento
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_precio", nullable = false, length = 10)
    private TipoPrecio tipoPrecio = TipoPrecio.LISTA;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

    // cantidad * precio - descuento
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
}
