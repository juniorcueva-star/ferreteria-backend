package com.ferreteria.config;

import com.ferreteria.dto.comun.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Swagger UI en http://localhost:8080/swagger-ui.html
 * Primero ejecutar POST /api/auth/login, copiar el token y pegarlo en el boton "Authorize".
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "API Ferreteria", version = "1.0",
                description = """
                        Gestion de 2 tiendas (cada una con su RUC) y 1 almacen: catalogo, compras, inventario,
                        traslados, caja, ventas al contado y al fiado, y reportes.

                        **Como probar:** 1) `POST /api/auth/login` con un usuario de demostracion;
                        2) copiar el `token`; 3) boton **Authorize** y pegarlo.

                        Todos los errores tienen el formato `{codigo, mensaje, detalle, fecha}`.
                        Los listados son paginados: `page` (desde 0), `size` (maximo 100) y `sort`."""),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    private static final Map<String, String> ERRORES = Map.of(
            "400", "Datos invalidos (VALIDACION o SOLICITUD_INVALIDA)",
            "401", "Sin token o token invalido (NO_AUTENTICADO)",
            "403", "Sin permiso por rol o por tienda (ACCESO_DENEGADO)",
            "404", "No existe (RECURSO_NO_ENCONTRADO)",
            "409", "Conflicto: duplicado, stock insuficiente o datos en conflicto",
            "422", "La operacion no cumple una regla del negocio (REGLA_NEGOCIO)");

    /** Agrega a cada endpoint las respuestas de error comunes con el esquema ErrorResponse. */
    @Bean
    public OpenApiCustomizer respuestasDeError() {
        return openApi -> {
            ModelConverters.getInstance().read(ErrorResponse.class).forEach(openApi.getComponents()::addSchemas);
            Schema<?> esquema = new Schema<>().$ref("#/components/schemas/ErrorResponse");
            Content contenido = new Content().addMediaType("application/json", new MediaType().schema(esquema));
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operacion -> {
                ApiResponses respuestas = operacion.getResponses();
                ERRORES.forEach((codigo, descripcion) -> respuestas.computeIfAbsent(codigo,
                        c -> new ApiResponse().description(descripcion).content(contenido)));
            }));
        };
    }
}
