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
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Empresa con su RUC. Cada tienda pertenece a una empresa.
 * Tabla: empresa
 */
@Entity
@Table(name = "empresa")
@Getter
@Setter
@NoArgsConstructor
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // BIGSERIAL: el id lo genera PostgreSQL
    private Long id;

    @Column(nullable = false, unique = true, length = 11)
    private String ruc;

    @Column(name = "razon_social", nullable = false, length = 150)
    private String razonSocial;

    @Column(name = "nombre_comercial", length = 150)
    private String nombreComercial;

    @Column(length = 200)
    private String direccion;

    /** Telefono o WhatsApp que se imprime en los documentos */
    @Column(length = 60)
    private String telefono;

    /** Frase bajo el nombre comercial, Ej: "Pernos y afines" */
    @Column(length = 100)
    private String lema;

    /** Cuentas para depositos (una por linea), se imprimen al pie de la cotizacion y la nota de venta */
    @Column(name = "datos_bancarios", length = 300)
    private String datosBancarios;

    @Column(nullable = false)
    private boolean activo = true;

    @CreationTimestamp // Hibernate pone la fecha y hora al insertar
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt; // TIMESTAMPTZ -> OffsetDateTime
}
