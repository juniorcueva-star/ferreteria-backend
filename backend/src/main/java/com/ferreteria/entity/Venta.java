package com.ferreteria.entity;

import com.ferreteria.entity.enums.CondicionVenta;
import com.ferreteria.entity.enums.EstadoVenta;
import com.ferreteria.entity.enums.TipoDocumentoVenta;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Venta de una tienda, al contado o a credito (fiado).
 * Tabla: venta
 */
@Entity
@Table(name = "venta")
@Getter
@Setter
@NoArgsConstructor
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubicacion_id", nullable = false)
    private Ubicacion ubicacion;

    // RUC de la tienda (la BD valida que coincida con la ubicacion)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caja_sesion_id", nullable = false)
    private CajaSesion cajaSesion;

    // null = cliente varios
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 12)
    private TipoDocumentoVenta tipoDocumento = TipoDocumentoVenta.NOTA_VENTA;

    @Column(nullable = false, length = 4)
    private String serie;

    @Column(nullable = false)
    private Integer numero;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CondicionVenta condicion = CondicionVenta.CONTADO;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    // Sin IGV
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal igv;

    // Informativo: suma de los descuentos ya aplicados en los detalles
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    // Deuda pendiente del fiado
    @Column(name = "saldo_pendiente", nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoPendiente = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoVenta estado = EstadoVenta.EMITIDA;

    // Fase 2 (comprobantes electronicos)
    @Column(name = "estado_sunat", length = 15)
    private String estadoSunat;

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<VentaDetalle> detalles = new ArrayList<>();

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<Pago> pagos = new ArrayList<>();

    public void agregarDetalle(VentaDetalle detalle) {
        detalle.setVenta(this);
        detalles.add(detalle);
    }

    public void agregarPago(Pago pago) {
        pago.setVenta(this);
        pagos.add(pago);
    }

    public String getNumeroDocumento() {
        return serie + "-" + String.format("%06d", numero);
    }
}
