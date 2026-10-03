package com.ferreteria.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Forma de pago (efectivo, Yape, Plin, deposito...). Catalogo cargado por la migracion V2.
 * Tabla: metodo_pago
 */
@Entity
@Table(name = "metodo_pago")
@Getter
@Setter
@NoArgsConstructor
public class MetodoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, length = 50)
    private String nombre;

    // Si es true se exige el numero de operacion (Yape, deposito...)
    @Column(name = "requiere_referencia", nullable = false)
    private boolean requiereReferencia;

    // Solo los pagos en efectivo cuentan para el cuadre de caja
    @Column(name = "es_efectivo", nullable = false)
    private boolean efectivo;

    @Column(nullable = false)
    private boolean activo = true;
}
