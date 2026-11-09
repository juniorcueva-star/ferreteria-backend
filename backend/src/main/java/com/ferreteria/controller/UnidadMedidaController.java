package com.ferreteria.controller;

import com.ferreteria.dto.catalogo.UnidadMedidaResponse;
import com.ferreteria.service.UnidadMedidaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Unidades de medida", description = "Catalogo de unidades de venta (docena, ciento, millar, caja, kilo...)")
@RestController
@RequestMapping("/api/unidades-medida")
@RequiredArgsConstructor
public class UnidadMedidaController {

    private final UnidadMedidaService unidadMedidaService;

    @Operation(summary = "Listar unidades de medida activas", description = "Se usa el campo 'codigo' al asignar unidades")
    @GetMapping
    public List<UnidadMedidaResponse> listar() {
        return unidadMedidaService.listarActivas();
    }
}
