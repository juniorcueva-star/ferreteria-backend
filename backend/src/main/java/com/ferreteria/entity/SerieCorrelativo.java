package com.ferreteria.entity;

import com.ferreteria.entity.enums.TipoDocumentoVenta;
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

/**
 * Ultimo numero usado por cada serie de documentos de una tienda (Ej: NV01 -> 245).
 * Tabla: serie_correlativo
 */
@Entity
@Table(name = "serie_correlativo")
@Getter
@Setter
@NoArgsConstructor
public class SerieCorrelativo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubicacion_id", nullable = false)
    private Ubicacion ubicacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 12)
    private TipoDocumentoVenta tipoDocumento;

    @Column(nullable = false, length = 4)
    private String serie;

    @Column(name = "ultimo_numero", nullable = false)
    private Integer ultimoNumero = 0;
}
