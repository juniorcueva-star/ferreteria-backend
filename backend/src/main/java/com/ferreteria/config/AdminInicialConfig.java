package com.ferreteria.config;

import com.ferreteria.service.UsuarioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Al arrancar, crea el usuario ADMIN inicial si todavia no existe ninguno.
 * Usuario y contrasena vienen de las variables ADMIN_USERNAME y ADMIN_PASSWORD (nunca del codigo).
 */
@Configuration
public class AdminInicialConfig {

    @Bean
    @Order(1)
    public ApplicationRunner crearAdminInicial(UsuarioService usuarioService,
                                               @Value("${app.admin.username}") String username,
                                               @Value("${app.admin.password:}") String password) {
        return args -> usuarioService.crearAdminInicialSiNoExiste(username, password);
    }
}
