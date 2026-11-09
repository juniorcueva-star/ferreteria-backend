package com.ferreteria.service;

import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Envio de correos con un PDF adjunto. Si el SMTP no esta configurado (MAIL_HOST y MAIL_FROM) responde 422 con un
 * mensaje claro, sin afectar al resto del sistema.
 */
@Slf4j
@Service
public class CorreoService {

    private final ObjectProvider<JavaMailSender> servidor;
    private final String remitente;

    public CorreoService(ObjectProvider<JavaMailSender> servidor, @Value("${app.correo.remitente:}") String remitente) {
        this.servidor = servidor;
        this.remitente = remitente;
    }

    public void enviarConPdf(String destinatario, String asunto, String texto, String nombreArchivo, byte[] pdf) {
        JavaMailSender sender = servidor.getIfAvailable();
        if (sender == null || remitente.isBlank()) {
            throw new ApiException(CodigoError.REGLA_NEGOCIO, "El envio de correos no esta configurado: defina "
                    + "MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD y MAIL_FROM en el servidor");
        }
        try {
            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper ayudante = new MimeMessageHelper(mensaje, true, "UTF-8");
            ayudante.setFrom(remitente);
            ayudante.setTo(destinatario);
            ayudante.setSubject(asunto);
            ayudante.setText(texto);
            ayudante.addAttachment(nombreArchivo, new ByteArrayResource(pdf), "application/pdf");
            sender.send(mensaje);
        } catch (MessagingException | MailException e) {
            log.warn("No se pudo enviar el correo a {}: {}", destinatario, e.getMessage());
            throw new ApiException(CodigoError.SERVICIO_EXTERNO, "No se pudo enviar el correo. Revise la direccion "
                    + "o la configuracion del servidor de correo");
        }
    }
}
