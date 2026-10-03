package com.ferreteria.config;

import com.ferreteria.service.UsuarioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, crea el usuario ADMIN inicial si todavia no existe ninguno.
 * Usuario y contrasena vienen de las variables ADMIN_USERNAME y ADMIN_PASSWORD (nunca del codigo).
 */
@Component
@Order(1)
public class AdminInicialConfig implements ApplicationRunner {

    private final UsuarioService usuarioService;
    private final String username;
    private final String password;

    public AdminInicialConfig(UsuarioService usuarioService, @Value("${app.admin.username}") String username,
                              @Value("${app.admin.password:}") String password) {
        this.usuarioService = usuarioService;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        usuarioService.crearAdminInicialSiNoExiste(username, password);
    }
}
