package com.ferreteria.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Servidor de correo (SMTP) para enviar cotizaciones. Solo se crea si existe MAIL_HOST: sin el, el envio responde
 * 422 y el resto del sistema funciona igual. Las credenciales vienen de variables de entorno.
 */
@Configuration
@ConditionalOnExpression("!'${app.correo.host:}'.isBlank()")
public class CorreoConfig {

    @Bean
    public JavaMailSender javaMailSender(@Value("${app.correo.host}") String host,
                                         @Value("${app.correo.puerto}") int puerto,
                                         @Value("${app.correo.usuario:}") String usuario,
                                         @Value("${app.correo.password:}") String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(puerto);
        sender.setDefaultEncoding("UTF-8");
        Properties propiedades = sender.getJavaMailProperties();
        propiedades.put("mail.smtp.starttls.enable", "true");
        propiedades.put("mail.smtp.connectiontimeout", "10000");
        propiedades.put("mail.smtp.timeout", "10000");
        propiedades.put("mail.smtp.writetimeout", "10000");
        if (!usuario.isBlank()) {
            sender.setUsername(usuario);
            sender.setPassword(password);
            propiedades.put("mail.smtp.auth", "true");
        }
        return sender;
    }
}
