package com.ferreteria.entity;

import com.ferreteria.entity.enums.EstadoSolicitud;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido de reposicion de una tienda al almacen. No mueve stock: al atenderlo se crea un traslado.
 * Tabla: solicitud_reposicion
 */
@Entity
@Table(name = "solicitud_reposicion")
@Getter
@Setter
@NoArgsConstructor
public class SolicitudReposicion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    /** Tienda que pide */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubicacion_id", nullable = false)
    private Ubicacion ubicacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    @Column(length = 300)
    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendida_por")
    private Usuario atendidaPor;

    @Column(name = "atendida_at")
    private OffsetDateTime atendidaAt;

    /** Motivo del rechazo o de la anulacion */
    @Column(length = 300)
    private String motivo;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "traslado_id")
    private Traslado traslado;

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @BatchSize(size = 100)
    private List<SolicitudReposicionDetalle> detalles = new ArrayList<>();

    public void agregarDetalle(SolicitudReposicionDetalle detalle) {
        detalle.setSolicitud(this);
        detalles.add(detalle);
    }
}
