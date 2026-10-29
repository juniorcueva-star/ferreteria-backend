package com.ferreteria.repository;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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

    /** Filtra por una lista de valores; una lista vacia no restringe el resultado. */
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

    /** Cada palabra debe aparecer en algun campo, sin distinguir mayusculas ni tildes. */
    public static <T> Specification<T> contienePalabras(String texto, String... rutas) {
        List<String> tokens = BusquedaTexto.tokens(texto);
        if (tokens.isEmpty()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.and(tokens.stream()
                .map(token -> cb.or(Arrays.stream(rutas)
                        .map(ruta -> cb.like(normalizado(cb, ruta(root, ruta)), "%" + escaparLike(token) + "%", '\\'))
                        .toArray(Predicate[]::new)))
                .toArray(Predicate[]::new));
    }

    /** Ordena una busqueda de catalogo: codigo exacto, prefijo de nombre y finalmente nombre. */
    public static <T> Specification<T> ordenarBusqueda(String texto, String rutaCodigo, String rutaNombre) {
        List<String> tokens = BusquedaTexto.tokens(texto);
        if (tokens.isEmpty()) {
            return Specification.unrestricted();
        }
        String consulta = BusquedaTexto.normalizar(texto);
        String primerToken = tokens.getFirst();
        return (root, query, cb) -> {
            // La consulta de conteo de Page no admite ORDER BY.
            if (!Long.class.equals(query.getResultType())) {
                Expression<String> codigo = normalizado(cb, ruta(root, rutaCodigo));
                Expression<String> nombre = normalizado(cb, ruta(root, rutaNombre));
                Expression<Integer> coincidenciaExacta = cb.<Integer>selectCase()
                        .when(cb.equal(codigo, consulta), 0).otherwise(1);
                Expression<Integer> prefijoNombre = cb.<Integer>selectCase()
                        .when(cb.like(nombre, primerToken + "%", '\\'), 0).otherwise(1);
                query.orderBy(cb.asc(coincidenciaExacta), cb.asc(prefijoNombre), cb.asc(nombre));
            }
            return cb.conjunction();
        };
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

    /** PostgreSQL translate evita requerir la extension unaccent para el catalogo actual. */
    private static Expression<String> normalizado(jakarta.persistence.criteria.CriteriaBuilder cb, Expression<String> expresion) {
        return cb.function("translate", String.class, cb.lower(expresion),
                cb.literal("áéíóúüñ"), cb.literal("aeiouun"));
    }
}
