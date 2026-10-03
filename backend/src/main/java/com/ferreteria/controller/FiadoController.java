package com.ferreteria.controller;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.fiado.DeudorResponse;
import com.ferreteria.dto.ventas.AbonoRequest;
import com.ferreteria.dto.ventas.VentaResponse;
import com.ferreteria.service.FiadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "15. Fiado", description = "Abonos a ventas al credito y consulta de deudas. ADMIN y VENDEDOR "
        + "(el vendedor solo su tienda)")
@RestController
@RequestMapping("/api/fiado")
@RequiredArgsConstructor
public class FiadoController {

    private final FiadoService fiadoService;

    @Operation(summary = "Registrar abono", description = "Pago parcial o total de una venta al credito, en la caja "
            + "abierta del usuario y en la misma tienda de la venta. Admite varios metodos de pago")
    @PostMapping("/ventas/{ventaId}/abonos")
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse registrarAbono(@PathVariable Long ventaId, @Valid @RequestBody AbonoRequest request) {
        return fiadoService.registrarAbono(ventaId, request);
    }

    @Operation(summary = "Ventas con deuda pendiente", description = "Filtros: ubicacionId, clienteId y "
            + "soloVencidas=true")
    @GetMapping("/deudas")
    public PaginaResponse<VentaResponse> listarDeudas(
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(defaultValue = "false") boolean soloVencidas,
            @ParameterObject @PageableDefault(size = 20, sort = "fecha") Pageable pageable) {
        return fiadoService.listarDeudas(ubicacionId, clienteId, soloVencidas, pageable);
    }

    @Operation(summary = "Reporte de deudores", description = "Clientes con deuda, de mayor a menor")
    @GetMapping("/deudores")
    public PaginaResponse<DeudorResponse> listarDeudores(@RequestParam(required = false) Long ubicacionId,
                                                         @ParameterObject Pageable pageable) {
        return fiadoService.listarDeudores(ubicacionId, pageable);
    }
}
