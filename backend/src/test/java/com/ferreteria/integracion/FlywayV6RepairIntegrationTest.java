package com.ferreteria.integracion;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/** Valida metadatos Flyway en la base aislada; no ejecuta reparaciones ni limpia datos. */
@SpringBootTest
@ActiveProfiles("integration")
class FlywayV6RepairIntegrationTest {
    @Autowired DataSource dataSource;

    @Test
    void validateConfirmaChecksumDeV6() throws Exception {
        try (Connection conexion = dataSource.getConnection()) {
            assertThat(conexion.getMetaData().getURL()).isEqualTo("jdbc:postgresql://127.0.0.1:5546/ivera_cotizacion_it");
            assertThat(conexion.getMetaData().getUserName()).isEqualTo("ivera_cotizacion_it_app");
        }
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }
}
