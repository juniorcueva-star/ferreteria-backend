package com.ferreteria.controller;

import com.ferreteria.dto.asistente.EstadoAsistente;
import com.ferreteria.dto.asistente.PreguntaRequest;
import com.ferreteria.dto.asistente.RespuestaAsistente;
import com.ferreteria.service.ia.AsistenteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Asistente de IA")
@RestController
@RequestMapping("/api/asistente")
@RequiredArgsConstructor
public class AsistenteController {

    private final AsistenteService asistenteService;

    @Operation(summary = "Estado del asistente", description = "Si la IA esta configurada, el nombre y las preguntas "
            + "sugeridas del rol y cuantas preguntas le quedan hoy al usuario")
    @GetMapping("/estado")
    public EstadoAsistente estado() {
        return asistenteService.estado();
    }

    @Operation(summary = "Preguntar al asistente", description = "Solo consulta datos (productos, precios, stock; "
            + "el ADMIN tambien ventas, lo mas vendido y deudores) con los permisos del usuario. No registra ni "
            + "cambia nada. 422 si falta la clave de la IA o se llego al tope diario")
    @PostMapping("/preguntar")
    public RespuestaAsistente preguntar(@Valid @RequestBody PreguntaRequest request) {
        return asistenteService.preguntar(request);
    }
}
