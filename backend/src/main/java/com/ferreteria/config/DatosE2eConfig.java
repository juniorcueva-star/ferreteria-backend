package com.ferreteria.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.SQLException;

/** Restablece solo datos operativos de la base aislada antes de cargar el escenario E2E reproducible. */
@Component
@Profile("e2e")
@Order(0)
public class DatosE2eConfig implements ApplicationRunner {

    private static final String TABLAS = String.join(", ",
            "movimiento_inventario", "pago", "venta_detalle", "venta", "serie_correlativo", "caja_sesion",
            "compra_detalle", "compra", "proveedor", "traslado_detalle", "traslado", "stock", "presentacion",
            "producto", "cliente", "usuario", "ubicacion", "empresa");

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final String baseEsperada;
    private final String usuarioEsperado;
    private final int puertoClienteEsperado;
    private final int puertoServidorEsperado;
    private final boolean resetEscenario;

    public DatosE2eConfig(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate,
                          @Value("${app.pruebas.base-esperada}") String baseEsperada,
                          @Value("${app.pruebas.usuario-esperado}") String usuarioEsperado,
                          @Value("${app.pruebas.puerto-cliente-esperado}") int puertoClienteEsperado,
                          @Value("${app.pruebas.puerto-servidor-esperado}") int puertoServidorEsperado,
                          @Value("${app.pruebas.reset-escenario}") boolean resetEscenario) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.baseEsperada = baseEsperada;
        this.usuarioEsperado = usuarioEsperado;
        this.puertoClienteEsperado = puertoClienteEsperado;
        this.puertoServidorEsperado = puertoServidorEsperado;
        this.resetEscenario = resetEscenario;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!resetEscenario) {
            return;
        }
        transactionTemplate.executeWithoutResult(estado -> jdbcTemplate.execute((ConnectionCallback<Void>) conexion -> {
            verificarConexionAislada(conexion);
            try (var sentencia = conexion.createStatement()) {
                sentencia.execute("TRUNCATE TABLE " + TABLAS + " RESTART IDENTITY CASCADE");
                sentencia.execute("ALTER SEQUENCE traslado_codigo_seq RESTART WITH 1");
            }
            return null;
        }));
    }

    private void verificarConexionAislada(Connection conexion) {
        try (var consulta = conexion.createStatement();
             var resultado = consulta.executeQuery("""
                     select current_database() as base, inet_server_port() as puerto_servidor, current_user as usuario
                     """)) {
            resultado.next();
            String url = conexion.getMetaData().getURL();
            boolean puertoClienteValido = url.contains(":" + puertoClienteEsperado + "/");
            boolean identidadValida = baseEsperada.equals(resultado.getString("base"))
                    && usuarioEsperado.equals(resultado.getString("usuario"))
                    && puertoServidorEsperado == resultado.getInt("puerto_servidor")
                    && puertoClienteValido;
            if (!identidadValida) {
                throw new IllegalStateException("Se rechazo limpiar una base que no es el entorno E2E aislado");
            }
        } catch (SQLException error) {
            throw new IllegalStateException("No se pudo verificar la conexion E2E aislada", error);
        }
    }
}
