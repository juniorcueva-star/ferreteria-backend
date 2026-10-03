package com.ferreteria.entity;

import com.ferreteria.entity.enums.EstadoTraslado;
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Envio de mercaderia entre ubicaciones (normalmente del almacen a una tienda).
 * Al enviar sale el stock del origen; al recibir entra al destino.
 * Tabla: traslado
 */
@Entity
@Table(name = "traslado")
@Getter
@Setter
@NoArgsConstructor
public class Traslado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origen_id", nullable = false)
    private Ubicacion origen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destino_id", nullable = false)
    private Ubicacion destino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoTraslado estado = EstadoTraslado.ENVIADO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_envia_id", nullable = false)
    private Usuario usuarioEnvia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_recibe_id")
    private Usuario usuarioRecibe;

    @CreationTimestamp
    @Column(name = "fecha_envio", nullable = false, updatable = false)
    private OffsetDateTime fechaEnvio;

    @Column(name = "fecha_recepcion")
    private OffsetDateTime fechaRecepcion;

    @Column(length = 300)
    private String observacion;

    @OneToMany(mappedBy = "traslado", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<TrasladoDetalle> detalles = new ArrayList<>();

    public void agregarDetalle(TrasladoDetalle detalle) {
        detalle.setTraslado(this);
        detalles.add(detalle);
    }
}
