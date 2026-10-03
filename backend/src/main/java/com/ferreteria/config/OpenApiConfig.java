package com.ferreteria.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI en http://localhost:8080/swagger-ui.html
 * Primero ejecutar POST /api/auth/login, copiar el token y pegarlo en el boton "Authorize".
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "API Ferreteria", version = "1.0",
                description = "Gestion de 2 tiendas y 1 almacen: catalogo, compras, inventario, traslados, "
                        + "caja, ventas al contado y al fiado, y reportes."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
