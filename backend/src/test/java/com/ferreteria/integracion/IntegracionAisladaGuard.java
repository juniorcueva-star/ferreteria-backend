package com.ferreteria.integracion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;

/** Rechaza el perfil integration si no apunta a su PostgreSQL exclusivo. */
@Component
@Profile("integration")
class IntegracionAisladaGuard {

    IntegracionAisladaGuard(JdbcTemplate jdbcTemplate,
                            @Value("${spring.datasource.url}") String url,
                            @Value("${app.integracion.host-esperado}") String hostEsperado,
                            @Value("${app.integracion.puerto-esperado}") int puertoEsperado,
                            @Value("${app.integracion.base-esperada}") String baseEsperada,
                            @Value("${app.integracion.usuario-esperado}") String usuarioEsperado) {
        URI uri = URI.create(url.substring("jdbc:".length()));
        var identidad = jdbcTemplate.queryForMap("select current_database() as base, current_user as usuario");
        boolean valida = hostEsperado.equals(uri.getHost())
                && puertoEsperado == uri.getPort()
                && ("/" + baseEsperada).equals(uri.getPath())
                && baseEsperada.equals(identidad.get("base"))
                && usuarioEsperado.equals(identidad.get("usuario"));
        if (!valida) throw new IllegalStateException("Se rechazo una conexion que no es PostgreSQL de integracion aislado");
    }
}
