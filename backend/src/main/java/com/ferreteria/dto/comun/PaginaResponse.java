package com.ferreteria.dto.comun;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Pagina de resultados de un listado. Todos los listados de la API devuelven este formato.
 *
 * @param contenido      elementos de la pagina actual
 * @param pagina         numero de pagina (empieza en 0)
 * @param tamano         cantidad maxima de elementos por pagina
 * @param totalElementos total de elementos que cumplen el filtro
 * @param totalPaginas   total de paginas
 */
public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {

    public static <T> PaginaResponse<T> de(Page<T> page) {
        return new PaginaResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public static <E, T> PaginaResponse<T> de(Page<E> page, Function<E, T> mapper) {
        return de(page.map(mapper));
    }
}
