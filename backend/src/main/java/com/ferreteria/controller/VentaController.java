package com.ferreteria.controller;

import com.ferreteria.dto.comun.AnulacionRequest;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.ventas.VentaRequest;
import com.ferreteria.dto.ventas.VentaResponse;
import com.ferreteria.entity.enums.CondicionVenta;
import com.ferreteria.entity.enums.EstadoVenta;
import com.ferreteria.service.VentaService;
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

@Tag(name = "14. Ventas", description = "Ventas al contado y al credito con pago mixto. ADMIN y VENDEDOR "
        + "(el vendedor solo en su tienda y con su caja abierta)")
@RestController
@RequestMapping("/api/ventas")
@PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService ventaService;

    @Operation(summary = "Registrar venta", description = "Se emite en la tienda de la caja abierta del usuario. "
            + "Descuenta stock (con bloqueo para que dos ventas no vendan lo mismo), deja kardex y registra los pagos")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse registrar(@Valid @RequestBody VentaRequest request) {
        return ventaService.registrar(request);
    }

    @Operation(summary = "Obtener una venta con detalle y pagos")
    @GetMapping("/{id}")
    public VentaResponse obtener(@PathVariable Long id) {
        return ventaService.obtener(id);
    }

    @Operation(summary = "Listar ventas", description = "Filtros: ubicacionId, usuarioId (vendedor), clienteId, "
            + "cajaId, condicion, estado, conSaldo (true = con deuda pendiente), desde, hasta")
    @GetMapping
    public PaginaResponse<VentaResponse> listar(
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) CondicionVenta condicion,
            @RequestParam(required = false) EstadoVenta estado,
            @RequestParam(required = false) Boolean conSaldo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @ParameterObject @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ventaService.listar(ubicacionId, usuarioId, clienteId, cajaId, condicion, estado, conSaldo, desde,
                hasta, pageable);
    }

    @Operation(summary = "Anular venta", description = "Devuelve el stock, anula los pagos y deja el saldo en 0. "
            + "Solo si las cajas donde se cobro siguen abiertas")
    @PostMapping("/{id}/anular")
    public VentaResponse anular(@PathVariable Long id, @Valid @RequestBody AnulacionRequest request) {
        return ventaService.anular(id, request);
    }
}
