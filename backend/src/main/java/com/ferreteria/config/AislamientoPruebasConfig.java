package com.ferreteria.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.sql.ResultSet;

/** Impide que Flyway de pruebas migre una base distinta de la exclusiva del Sprint 16.1. */
@Configuration
@Profile({"test", "e2e"})
public class AislamientoPruebasConfig {

    @Bean
    FlywayMigrationStrategy verificarBaseAntesDeMigrar(@Value("${app.pruebas.base-esperada}") String baseEsperada) {
        return flyway -> {
            try (var conexion = flyway.getConfiguration().getDataSource().getConnection();
                 var consulta = conexion.createStatement();
                 ResultSet resultado = consulta.executeQuery("select current_database()")) {
                resultado.next();
                if (!baseEsperada.equals(resultado.getString(1))) {
                    throw new IllegalStateException("La base de pruebas no coincide con la base aislada configurada");
                }
            } catch (java.sql.SQLException error) {
                throw new IllegalStateException("No se pudo verificar la base de pruebas aislada", error);
            }
            flyway.migrate();
        };
    }
}
