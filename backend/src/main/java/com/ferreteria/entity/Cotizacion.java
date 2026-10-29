package com.ferreteria.entity;

import com.ferreteria.entity.enums.EstadoCotizacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "cotizacion") @Getter @Setter @NoArgsConstructor
public class Cotizacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 40) private String codigo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ubicacion_id") private Ubicacion ubicacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cliente_id") private Cliente cliente;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "usuario_id") private Usuario usuario;
    @CreationTimestamp @Column(nullable = false, updatable = false) private OffsetDateTime fecha;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private EstadoCotizacion estado = EstadoCotizacion.BORRADOR;
    @Column(nullable = false, length = 3) private String moneda = "PEN";
    @Column(length = 300) private String observacion;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal subtotal;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal igv;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal descuento = BigDecimal.ZERO;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal total;
    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("id") private List<CotizacionDetalle> detalles = new ArrayList<>();
    public void agregarDetalle(CotizacionDetalle detalle) { detalle.setCotizacion(this); detalles.add(detalle); }
    public void reemplazarDetalles(List<CotizacionDetalle> nuevos) { detalles.clear(); nuevos.forEach(this::agregarDetalle); }
}
