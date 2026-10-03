package com.ferreteria.controller;

import com.ferreteria.dto.comun.AnulacionRequest;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.compras.CompraRequest;
import com.ferreteria.dto.compras.CompraResponse;
import com.ferreteria.entity.enums.EstadoCompra;
import com.ferreteria.service.CompraService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "08. Compras", description = "Compras a proveedores: la mercaderia entra al stock. ADMIN y ALMACENERO "
        + "(el almacenero solo en su almacen). Anular: solo ADMIN")
@RestController
@RequestMapping("/api/compras")
@PreAuthorize("hasAnyRole('ADMIN', 'ALMACENERO')")
@RequiredArgsConstructor
public class CompraController {

    private final CompraService compraService;

    @Operation(summary = "Listar compras", description = "Filtros: proveedorId, empresaId, ubicacionId, estado y "
            + "rango de fecha de emision (desde/hasta, formato AAAA-MM-DD)")
    @GetMapping
    public PaginaResponse<CompraResponse> listar(
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) Long empresaId,
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(required = false) EstadoCompra estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @ParameterObject @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return compraService.listar(proveedorId, empresaId, ubicacionId, estado, desde, hasta, pageable);
    }

    @Operation(summary = "Obtener una compra con su detalle")
    @GetMapping("/{id}")
    public CompraResponse obtener(@PathVariable Long id) {
        return compraService.obtener(id);
    }

    @Operation(summary = "Registrar compra", description = "Suma el stock en la ubicacion y deja el kardex. "
            + "precioUnitario es el precio por presentacion (con IGV) como figura en el comprobante")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompraResponse registrar(@Valid @RequestBody CompraRequest request) {
        return compraService.registrar(request);
    }

    @Operation(summary = "Anular compra", description = "Retira el stock que entro. Falla si ya se vendio o traslado")
    @PostMapping("/{id}/anular")
    @PreAuthorize("hasRole('ADMIN')")
    public CompraResponse anular(@PathVariable Long id, @Valid @RequestBody AnulacionRequest request) {
        return compraService.anular(id, request);
    }
}
