package com.ferreteria.exception;

import com.ferreteria.dto.comun.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.List;

/**
 * Convierte cualquier excepcion en un ErrorResponse con el mismo formato (codigo, mensaje, detalle, fecha).
 * Los mensajes nunca exponen SQL ni trazas internas.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> manejarApi(ApiException ex) {
        return responder(ex.getCodigo(), ex.getMessage(), ex.getDetalle());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(MethodArgumentNotValidException ex) {
        List<String> detalle = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError campo
                        ? campo.getField() + ": " + campo.getDefaultMessage()
                        : error.getDefaultMessage())
                .sorted()
                .toList();
        return responder(CodigoError.VALIDACION, "Los datos enviados no son validos", detalle);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> manejarValidacionParametros(HandlerMethodValidationException ex) {
        List<String> detalle = ex.getParameterValidationResults().stream()
                .flatMap(resultado -> resultado.getResolvableErrors().stream()
                        .map(error -> resultado.getMethodParameter().getParameterName() + ": "
                                + error.getDefaultMessage()))
                .sorted()
                .toList();
        return responder(CodigoError.VALIDACION, "Los parametros enviados no son validos", detalle);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> manejarRestricciones(ConstraintViolationException ex) {
        List<String> detalle = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .toList();
        return responder(CodigoError.VALIDACION, "Los datos enviados no son validos", detalle);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarJsonInvalido(HttpMessageNotReadableException ex) {
        return responder(CodigoError.SOLICITUD_INVALIDA,
                "El cuerpo de la solicitud no es un JSON valido o tiene valores con formato incorrecto", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> manejarTipoIncorrecto(MethodArgumentTypeMismatchException ex) {
        return responder(CodigoError.SOLICITUD_INVALIDA, "Parametro con formato incorrecto",
                List.of(ex.getName() + ": valor '" + ex.getValue() + "' no valido"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> manejarParametroFaltante(MissingServletRequestParameterException ex) {
        return responder(CodigoError.SOLICITUD_INVALIDA, "Falta un parametro obligatorio",
                List.of(ex.getParameterName() + ": es obligatorio"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> manejarArchivoFaltante(MissingServletRequestPartException ex) {
        return responder(CodigoError.SOLICITUD_INVALIDA, "Falta el archivo",
                List.of(ex.getRequestPartName() + ": es obligatorio"));
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> manejarOrdenInvalido(PropertyReferenceException ex) {
        return responder(CodigoError.SOLICITUD_INVALIDA, "No se puede ordenar por ese campo",
                List.of("sort: '" + ex.getPropertyName() + "' no existe"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> manejarMetodo(HttpRequestMethodNotSupportedException ex) {
        return responder(CodigoError.METODO_NO_PERMITIDO, "Metodo HTTP no permitido: " + ex.getMethod(), List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> manejarContentType(HttpMediaTypeNotSupportedException ex) {
        return responder(CodigoError.SOLICITUD_INVALIDA, "Content-Type no soportado: " + ex.getContentType(),
                List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> manejarRutaInexistente(NoResourceFoundException ex) {
        return responder(CodigoError.RECURSO_NO_ENCONTRADO, "La ruta solicitada no existe",
                List.of("/" + ex.getResourcePath()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> manejarArchivoGrande(MaxUploadSizeExceededException ex) {
        return responder(CodigoError.ARCHIVO_DEMASIADO_GRANDE, "El archivo supera el tamano maximo permitido",
                List.of());
    }

    /** Ultima barrera: una restriccion de la BD (CHECK, UNIQUE, FK) rechazo la operacion. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> manejarIntegridad(DataIntegrityViolationException ex) {
        log.warn("Violacion de integridad: {}", ex.getMostSpecificCause().getMessage());
        // SQLState 22xxx = dato invalido para la columna (Ej: un numero demasiado grande)
        if (ex.getMostSpecificCause() instanceof SQLException sql && sql.getSQLState() != null
                && sql.getSQLState().startsWith("22")) {
            return responder(CodigoError.SOLICITUD_INVALIDA, "Algun valor excede el rango permitido", List.of());
        }
        return responder(CodigoError.CONFLICTO_DATOS,
                "La operacion no se pudo completar porque los datos entran en conflicto con registros existentes",
                List.of());
    }

    /** Bloqueo entre transacciones simultaneas (Ej: deadlock): el cliente puede reintentar. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> manejarBloqueo(PessimisticLockingFailureException ex) {
        log.warn("Conflicto de bloqueo: {}", ex.getMessage());
        return responder(CodigoError.CONFLICTO_DATOS,
                "Otra operacion estaba usando los mismos datos. Intente nuevamente", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarInesperado(Exception ex) {
        log.error("Error inesperado", ex);
        return responder(CodigoError.ERROR_INTERNO, "Ocurrio un error inesperado. Intente nuevamente", List.of());
    }

    private ResponseEntity<ErrorResponse> responder(CodigoError codigo, String mensaje, List<String> detalle) {
        return ResponseEntity.status(codigo.getStatus()).body(ErrorResponse.de(codigo.name(), mensaje, detalle));
    }
}
