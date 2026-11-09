package com.ferreteria.integracion;

import com.ferreteria.entity.enums.UnidadBase;
import jakarta.mail.Address;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Envio de la cotizacion por correo con un servidor SMTP simulado: no se envian correos reales.
 */
@TestPropertySource(properties = "app.correo.remitente=ventas@ferreteria.pe")
class CotizacionCorreoIntegrationTest extends IntegracionTestBase {

    @MockitoBean private JavaMailSender servidorCorreo;

    @Test
    @DisplayName("Envia el correo con el PDF adjunto y la cotizacion queda EMITIDA")
    void enviaConPdfAdjunto() throws Exception {
        when(servidorCorreo.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        var perno = datos.producto("PER-1", UnidadBase.UNIDAD, "0.50");
        String vendedor = datos.token(datos.vendedor1);
        Long id = leerId(postCon(vendedor, "/api/cotizaciones", Map.of("detalles",
                List.of(Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 10)))).andReturn(), "$.id");

        postCon(vendedor, "/api/cotizaciones/{id}/enviar-correo",
                Map.of("destinatario", "cliente@correo.pe", "mensaje", "Quedamos atentos"), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EMITIDA"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(servidorCorreo).send(captor.capture());
        MimeMessage enviado = captor.getValue();
        Address[] para = enviado.getAllRecipients();
        assertThat(para[0].toString()).isEqualTo("cliente@correo.pe");
        assertThat(enviado.getSubject()).startsWith("Cotización COT-");
        Multipart partes = (Multipart) enviado.getContent();
        boolean tienePdf = false;
        for (int i = 0; i < partes.getCount(); i++) {
            tienePdf |= partes.getBodyPart(i).getFileName() != null && partes.getBodyPart(i).getFileName().endsWith(".pdf");
        }
        assertThat(tienePdf).isTrue();
    }
}
