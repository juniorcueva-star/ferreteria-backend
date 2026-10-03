package com.ferreteria.controller;

import com.ferreteria.dto.caja.AperturaCajaRequest;
import com.ferreteria.dto.caja.CajaResponse;
import com.ferreteria.dto.caja.CierreCajaRequest;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.entity.enums.EstadoCaja;
import com.ferreteria.service.CajaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "12. Caja", description = "Apertura y cierre de caja con cuadre. ADMIN y VENDEDOR")
@RestController
@RequestMapping("/api/cajas")
@RequiredArgsConstructor
public class CajaController {

    private final CajaService cajaService;

    @Operation(summary = "Abrir mi caja", description = "Una sola caja abierta por usuario")
    @PostMapping("/abrir")
    @ResponseStatus(HttpStatus.CREATED)
    public CajaResponse abrir(@Valid @RequestBody AperturaCajaRequest request) {
        return cajaService.abrir(request);
    }

    @Operation(summary = "Mi caja abierta con su resumen en vivo")
    @GetMapping("/actual")
    public CajaResponse obtenerActual() {
        return cajaService.obtenerActual();
    }

    @Operation(summary = "Cerrar caja con cuadre", description = "Recibe el efectivo contado y calcula la "
            + "diferencia contra el efectivo esperado (apertura + cobros en efectivo)")
    @PostMapping("/{id}/cerrar")
    public CajaResponse cerrar(@PathVariable Long id, @Valid @RequestBody CierreCajaRequest request) {
        return cajaService.cerrar(id, request);
    }

    @Operation(summary = "Obtener una caja con su resumen")
    @GetMapping("/{id}")
    public CajaResponse obtener(@PathVariable Long id) {
        return cajaService.obtener(id);
    }

    @Operation(summary = "Listar cajas", description = "Filtros: ubicacionId, usuarioId, estado, desde, hasta")
    @GetMapping
    public PaginaResponse<CajaResponse> listar(
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) EstadoCaja estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return cajaService.listar(ubicacionId, usuarioId, estado, desde, hasta, pageable);
    }
}
