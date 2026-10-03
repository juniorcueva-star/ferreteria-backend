package com.ferreteria.service;

import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

/**
 * Lista blanca de campos por los que se puede ordenar cada listado (parametro sort). Evita ordenar por datos
 * internos (Ej: el hash de la contrasena) o por colecciones, que duplicarian filas en la pagina.
 */
public final class Ordenamiento {

    private Ordenamiento() {
    }

    public static Pageable validar(Pageable pageable, String... permitidos) {
        Set<String> campos = Set.of(permitidos);
        List<String> invalidos = pageable.getSort().stream()
                .map(Sort.Order::getProperty)
                .filter(campo -> !campos.contains(campo))
                .toList();
        if (!invalidos.isEmpty()) {
            throw new ApiException(CodigoError.SOLICITUD_INVALIDA, "No se puede ordenar por " + invalidos,
                    List.of("sort: valores permitidos " + String.join(", ", permitidos)));
        }
        return pageable;
    }
}
