package com.ferreteria.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Publica la carpeta de imagenes locales en /imagenes/** (solo cuando no se usa Cloudinary).
 */
@Configuration
@ConditionalOnExpression("'${app.imagenes.cloudinary-url:}'.isBlank()")
public class ImagenesLocalesConfig implements WebMvcConfigurer {

    private final String directorio;

    public ImagenesLocalesConfig(@Value("${app.imagenes.directorio}") String directorio) {
        this.directorio = directorio;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String ubicacion = Path.of(directorio).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/imagenes/**").addResourceLocations(ubicacion);
    }
}
