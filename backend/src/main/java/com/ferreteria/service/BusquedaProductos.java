package com.ferreteria.service;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.SinonimoBusqueda;
import com.ferreteria.repository.SinonimoBusquedaRepository;
import com.ferreteria.util.BusquedaTexto;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Filtro de busqueda por palabras para productos (catalogo y disponibilidad): cada palabra debe aparecer al inicio
 * de una palabra del producto (codigo, nombre, marca, tipo, acabado o medida). Acepta abreviaturas ("per hex zin").
 */
@Component
@RequiredArgsConstructor
public class BusquedaProductos {

    private final SinonimoBusquedaRepository sinonimoBusquedaRepository;

    public Specification<Producto> coincidenPalabras(String texto) {
        List<String> palabras = BusquedaTexto.tokens(texto);
        if (palabras.isEmpty()) {
            return Specification.unrestricted();
        }
        Map<String, String> sinonimos = new HashMap<>();
        for (SinonimoBusqueda sinonimo : sinonimoBusquedaRepository.findAll()) {
            sinonimos.put(BusquedaTexto.normalizar(sinonimo.getAbreviatura()),
                    BusquedaTexto.normalizar(sinonimo.getTermino()));
        }
        return (root, query, cb) -> cb.and(palabras.stream()
                .map(palabra -> cb.or(BusquedaTexto.alternativas(palabra, sinonimos).stream()
                        .map(alternativa -> cb.like(root.<String>get("terminoBusqueda"),
                                BusquedaTexto.patronInicioPalabra(alternativa), '\\'))
                        .toArray(Predicate[]::new)))
                .toArray(Predicate[]::new));
    }
}
