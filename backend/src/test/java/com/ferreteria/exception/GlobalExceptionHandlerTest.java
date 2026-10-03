package com.ferreteria.exception;

import com.ferreteria.dto.comun.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Un error de negocio responde con su codigo, mensaje, detalle y fecha")
    void errorDeNegocio() {
        ResponseEntity<ErrorResponse> respuesta = handler.manejarApi(
                new StockInsuficienteException("Clavo 2\"", "Tienda 1", new BigDecimal("3.000"), new BigDecimal("5.000")));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.codigo()).isEqualTo("STOCK_INSUFICIENTE");
        assertThat(cuerpo.mensaje()).contains("Clavo 2\"");
        assertThat(cuerpo.detalle()).containsExactly("disponible: 3.000", "requerido: 5.000");
        assertThat(cuerpo.fecha()).isNotNull();
    }

    @Test
    @DisplayName("Una violacion de restriccion de la BD responde 409 sin exponer el SQL")
    void integridadNoExponeSql() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("fallo",
                new SQLException("ERROR: duplicate key value violates unique constraint \"uq_producto\""));

        ResponseEntity<ErrorResponse> respuesta = handler.manejarIntegridad(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().codigo()).isEqualTo("CONFLICTO_DATOS");
        assertThat(respuesta.getBody().mensaje()).doesNotContain("constraint", "uq_producto");
    }

    @Test
    @DisplayName("Un error inesperado responde 500 con mensaje generico")
    void errorInesperado() {
        ResponseEntity<ErrorResponse> respuesta = handler.manejarInesperado(new IllegalStateException("detalle interno"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().mensaje()).doesNotContain("detalle interno");
    }
}
