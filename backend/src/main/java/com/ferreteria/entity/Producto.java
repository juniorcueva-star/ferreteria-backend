package com.ferreteria.entity;

import com.ferreteria.entity.enums.UnidadBase;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Producto del catalogo. Su stock se guarda en la unidad base (unidad, kilo o metro).
 * Tabla: producto
 */
@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 300)
    private String descripcion;

    @Column(length = 80)
    private String marca;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_base", nullable = false, length = 10)
    private UnidadBase unidadBase;

    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    @Column(name = "imagen_public_id", length = 200)
    private String imagenPublicId;

    @Column(nullable = false)
    private boolean activo = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Formas de vender el producto (unidad, ciento, kilo, rollo...)
    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<Presentacion> presentaciones = new ArrayList<>();

    public void agregarPresentacion(Presentacion presentacion) {
        presentacion.setProducto(this);
        presentaciones.add(presentacion);
    }
}
