package com.ferreteria.dto.comun;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * Contrasena nueva: de 8 a 72 caracteres y como maximo 72 bytes, que es el limite de BCrypt
 * (una letra con tilde o una enie ocupa 2 bytes).
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Contrasena.Validador.class)
public @interface Contrasena {

    String message() default "debe tener entre 8 y 72 caracteres (sin superar 72 bytes)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<Contrasena, String> {

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext context) {
            return valor == null
                    || (valor.length() >= 8 && valor.getBytes(StandardCharsets.UTF_8).length <= 72);
        }
    }
}
