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

/** Pieza de un completo: una presentacion de un producto y su cantidad por cada completo. Tabla: completo_componente */
@Entity
@Table(name = "completo_componente")
@Getter
@Setter
@NoArgsConstructor
public class CompletoComponente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "completo_id", nullable = false)
    private Completo completo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentacion_id", nullable = false)
    private Presentacion presentacion;

    /** En la presentacion elegida (Ej: 6 tornillos por bisagra) */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidad;

    /** Lo que se descuenta del stock de la pieza por cada completo, en unidad base. */
    public BigDecimal cantidadBasePorCompleto() {
        return cantidad.multiply(presentacion.getFactor());
    }
}
