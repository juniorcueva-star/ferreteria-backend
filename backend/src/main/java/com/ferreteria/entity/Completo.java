package com.ferreteria.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "Completo" de un producto (pedido del dueno): se vende junto con otras piezas a un precio propio.
 * Ej: Perno hexagonal 3/8 x 2 - Completo = 1 perno + 1 tuerca + 1 arandela. No tiene stock propio.
 * Tabla: completo
 */
@Entity
@Table(name = "completo")
@Getter
@Setter
@NoArgsConstructor
public class Completo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Producto en cuya ficha aparece el completo (normalmente tambien es una de sus piezas) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(name = "precio_venta", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioVenta;

    @Column(nullable = false)
    private boolean activo = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "completo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @BatchSize(size = 100)
    private List<CompletoComponente> componentes = new ArrayList<>();

    public void agregarComponente(CompletoComponente componente) {
        componente.setCompleto(this);
        componentes.add(componente);
    }

    /** Texto de las piezas, Ej: "1 Perno hexagonal (Unidad) + 1 Tuerca 3/8 (Unidad)". */
    public String composicion() {
        return componentes.stream()
                .map(c -> c.getCantidad().stripTrailingZeros().toPlainString() + " "
                        + c.getProducto().getNombre() + " (" + c.getPresentacion().getNombre() + ")")
                .collect(Collectors.joining(" + "));
    }
}
