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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Forma de vender un producto con su propio precio. El factor indica cuantas unidades base contiene
 * (Ej: Ciento = 100, Medio kilo = 0.5, Rollo 30 m = 30).
 * Tabla: presentacion
 */
@Entity
@Table(name = "presentacion")
@Getter
@Setter
@NoArgsConstructor
public class Presentacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal factor;

    // Precio con IGV incluido
    @Column(name = "precio_venta", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioVenta;

    @Column(name = "codigo_barras", unique = true, length = 50)
    private String codigoBarras;

    // Decimales que acepta al vender (0 = enteros, 3 = kilo/metro)
    @Column(nullable = false)
    private int decimales;

    // Costo de la ultima compra por unidad base x factor (se actualiza con cada compra)
    @Column(name = "costo_referencia", precision = 12, scale = 4)
    private BigDecimal costoReferencia;

    @Column(name = "porcentaje_ganancia", precision = 8, scale = 4)
    private BigDecimal porcentajeGanancia;

    @Column(name = "monto_adicional", precision = 12, scale = 2)
    private BigDecimal montoAdicional;

    // Informativo: el precio de venta solo cambia cuando el usuario lo confirma
    @Column(name = "precio_sugerido", precision = 12, scale = 2)
    private BigDecimal precioSugerido;

    @Column(name = "es_principal", nullable = false)
    private boolean principal;

    @Column(nullable = false)
    private boolean activo = true;

    // Precio por mayor, de menor a mayor cantidad. BatchSize: al listar productos se cargan en una sola consulta
    @OneToMany(mappedBy = "presentacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("cantidadMinima")
    @BatchSize(size = 100)
    private List<EscalaPrecio> escalas = new ArrayList<>();

    /**
     * Precio de cada presentacion al llevar esta cantidad: el de la escala mas alta que se alcanza, si es menor
     * que el de lista. Ej: con "desde 100 a S/ 0.40", 120 unidades cuestan S/ 0.40 cada una.
     */
    public BigDecimal precioPara(BigDecimal cantidad) {
        BigDecimal precio = precioVenta;
        for (EscalaPrecio escala : escalas) {
            if (cantidad.compareTo(escala.getCantidadMinima()) >= 0 && escala.getPrecio().compareTo(precio) < 0) {
                precio = escala.getPrecio();
            }
        }
        return precio;
    }
}
