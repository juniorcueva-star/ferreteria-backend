package com.ferreteria.service.ia;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Claude por medio del SDK oficial de Anthropic. La clave sale de ANTHROPIC_API_KEY (nunca del frontend). Usa
 * esfuerzo bajo: las preguntas del asistente son simples y asi responde rapido y barato.
 */
@Slf4j
@Component
public class ModeloIaClaude implements ModeloIa {

    private static final long MAXIMO_TOKENS = 4096;

    private final String modelo;
    private final AnthropicClient cliente;

    public ModeloIaClaude(@Value("${app.ia.api-key:}") String apiKey, @Value("${app.ia.modelo}") String modelo) {
        this.modelo = modelo;
        this.cliente = apiKey == null || apiKey.isBlank() ? null : AnthropicOkHttpClient.builder()
                .apiKey(apiKey.strip())
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Override
    public boolean configurado() {
        return cliente != null;
    }

    @Override
    public Turno responder(String sistema, List<Herramienta> herramientas, List<Mensaje> conversacion) {
        if (cliente == null) {
            throw new ApiException(CodigoError.SERVICIO_EXTERNO, "El asistente no está configurado: falta la clave "
                    + "de la IA (ANTHROPIC_API_KEY)");
        }
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(modelo)
                .maxTokens(MAXIMO_TOKENS)
                .system(sistema)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build());
        herramientas.forEach(h -> params.addTool(herramienta(h)));
        for (Mensaje mensaje : conversacion) {
            switch (mensaje) {
                case DelUsuario u -> params.addUserMessage(u.texto());
                case DelAsistente a -> params.addAssistantMessage(a.texto());
                // La respuesta original va tal cual: conserva sus bloques de razonamiento entre herramientas
                case DelModelo m -> params.addMessage((Message) m.turno().original());
                case Resultados r -> params.addUserMessageOfContentBlockParams(r.resultados().stream()
                        .map(res -> ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                                .toolUseId(res.llamadaId())
                                .content(res.contenido())
                                .isError(res.error())
                                .build()))
                        .toList());
            }
        }
        try {
            return turno(cliente.messages().create(params.build()));
        } catch (UnauthorizedException | PermissionDeniedException e) {
            throw new ApiException(CodigoError.SERVICIO_EXTERNO, "La clave de la IA no es válida o venció: revise "
                    + "ANTHROPIC_API_KEY");
        } catch (RateLimitException e) {
            throw new ApiException(CodigoError.SERVICIO_EXTERNO, "La IA está recibiendo muchas preguntas: intente de "
                    + "nuevo en un minuto");
        } catch (BadRequestException e) {
            log.warn("La IA rechazo la solicitud: {}", e.getMessage());
            throw new ApiException(CodigoError.SERVICIO_EXTERNO, "La IA no pudo responder: revise el saldo y el tope "
                    + "de gasto de la cuenta (Billing)");
        } catch (AnthropicIoException e) {
            throw new ApiException(CodigoError.SERVICIO_EXTERNO, "No se pudo conectar con la IA: revise el internet "
                    + "del servidor");
        } catch (AnthropicServiceException e) {
            log.warn("La IA respondio con error {}: {}", e.statusCode(), e.getMessage());
            throw new ApiException(CodigoError.SERVICIO_EXTERNO, "La IA no respondió: intente de nuevo");
        }
    }

    private static Tool herramienta(Herramienta h) {
        Tool.InputSchema.Properties.Builder propiedades = Tool.InputSchema.Properties.builder();
        h.propiedades().forEach((nombre, esquema) -> propiedades.putAdditionalProperty(nombre, JsonValue.from(esquema)));
        return Tool.builder()
                .name(h.nombre())
                .description(h.descripcion())
                .inputSchema(Tool.InputSchema.builder()
                        .properties(propiedades.build())
                        .required(h.requeridas())
                        .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Turno turno(Message respuesta) {
        List<Bloque> bloques = new ArrayList<>();
        for (ContentBlock bloque : respuesta.content()) {
            bloque.text().ifPresent(t -> bloques.add(new Texto(t.text())));
            bloque.toolUse().ifPresent(t -> bloques.add(new Llamada(t.id(), t.name(), t._input().convert(Map.class))));
        }
        String fin = respuesta.stopReason().map(ModeloIaClaude::fin).orElse("END_TURN");
        return new Turno(bloques, fin, respuesta, respuesta.usage().inputTokens(), respuesta.usage().outputTokens());
    }

    private static String fin(StopReason razon) {
        if (StopReason.TOOL_USE.equals(razon)) {
            return "TOOL_USE";
        }
        if (StopReason.MAX_TOKENS.equals(razon)) {
            return "MAX_TOKENS";
        }
        if (StopReason.REFUSAL.equals(razon)) {
            return "REFUSAL";
        }
        return "END_TURN";
    }
}
