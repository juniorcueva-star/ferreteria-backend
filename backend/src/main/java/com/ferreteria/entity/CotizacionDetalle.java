package com.ferreteria.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity @Table(name = "cotizacion_detalle") @Getter @Setter @NoArgsConstructor
public class CotizacionDetalle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cotizacion_id") private Cotizacion cotizacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "producto_id") private Producto producto;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "presentacion_id") private Presentacion presentacion;
    @Column(nullable = false, length = 200) private String descripcion;
    @Column(nullable = false, precision = 14, scale = 3) private BigDecimal cantidad;
    @Column(nullable = false, precision = 14, scale = 3) private BigDecimal factor;
    @Column(name = "cantidad_base", nullable = false, precision = 14, scale = 3) private BigDecimal cantidadBase;
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2) private BigDecimal precioUnitario;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal descuento = BigDecimal.ZERO;
    @Column(name = "ajuste_porcentaje", precision = 12, scale = 4) private BigDecimal ajustePorcentaje;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal subtotal;
}
