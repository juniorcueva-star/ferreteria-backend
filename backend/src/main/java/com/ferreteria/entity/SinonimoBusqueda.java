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
 * Abreviatura que el personal usa al buscar y su termino completo (per -> perno).
 * Tabla: sinonimo_busqueda
 */
@Entity
@Table(name = "sinonimo_busqueda")
@Getter
@Setter
@NoArgsConstructor
public class SinonimoBusqueda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String abreviatura;

    @Column(nullable = false, length = 60)
    private String termino;
}
