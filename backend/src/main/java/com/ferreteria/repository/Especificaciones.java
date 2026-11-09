package com.ferreteria.repository;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

/**
 * Filtros reutilizables para los listados. Si el valor del filtro es null, el filtro no se aplica.
 * Las rutas aceptan puntos para navegar relaciones (Ej: "ubicacion.id").
 */
public final class Especificaciones {

    private Especificaciones() {
    }

    public static <T> Specification<T> igual(String ruta, Object valor) {
        if (valor == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(ruta(root, ruta), valor);
    }

    /** El valor esta en la lista (Ej: varios ids). Lista null o vacia = sin filtro. */
    public static <T> Specification<T> en(String ruta, Collection<?> valores) {
        if (valores == null || valores.isEmpty()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> ruta(root, ruta).in(valores);
    }

    /** Busca el texto (sin importar mayusculas) en cualquiera de las rutas indicadas. */
    public static <T> Specification<T> contiene(String texto, String... rutas) {
        if (texto == null || texto.isBlank()) {
            return Specification.unrestricted();
        }
        String patron = "%" + escaparLike(texto.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(Arrays.stream(rutas)
                .map(r -> cb.like(cb.lower(ruta(root, r).as(String.class)), patron, '\\'))
                .toArray(Predicate[]::new));
    }

    public static <T, Y extends Comparable<? super Y>> Specification<T> desde(String ruta, Y valor) {
        if (valor == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(ruta(root, ruta), valor);
    }

    /** Limite superior exclusivo (menor que). */
    public static <T, Y extends Comparable<? super Y>> Specification<T> antesDe(String ruta, Y valor) {
        if (valor == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.lessThan(ruta(root, ruta), valor);
    }

    @SuppressWarnings("unchecked")
    private static <Y> Expression<Y> ruta(Root<?> root, String ruta) {
        Path<?> path = root;
        for (String parte : ruta.split("\\.")) {
            path = path.get(parte);
        }
        return (Expression<Y>) path;
    }

    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
