package com.ferreteria.controller;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.ventas.MetodoPagoResponse;
import com.ferreteria.service.MetodoPagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "13. Metodos de pago", description = "Catalogo de formas de pago (efectivo, Yape, Plin...)")
@RestController
@RequestMapping("/api/metodos-pago")
@RequiredArgsConstructor
public class MetodoPagoController {

    private final MetodoPagoService metodoPagoService;

    @Operation(summary = "Listar metodos de pago", description = "En las ventas y abonos se usa el campo 'codigo'")
    @GetMapping
    public PaginaResponse<MetodoPagoResponse> listar(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return metodoPagoService.listar(pageable);
    }
}
