package com.ferreteria.controller;

import com.ferreteria.dto.comun.AnulacionRequest;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.encargos.EncargoRequest;
import com.ferreteria.dto.encargos.EncargoResponse;
import com.ferreteria.dto.encargos.EntregaEncargoRequest;
import com.ferreteria.entity.enums.EstadoEncargo;
import com.ferreteria.service.EncargoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

@Tag(name = "17. Encargos", description = "Productos sin stock que se consiguen fuera y se venden con precio propio. "
        + "ADMIN y VENDEDOR (el vendedor solo en su tienda). No tocan el stock hasta entregarse")
@RestController
@RequestMapping("/api/encargos")
@RequiredArgsConstructor
public class EncargoController {

    private final EncargoService encargoService;

    @Operation(summary = "Registrar encargo", description = "No descuenta stock. El precio no puede ser menor que el "
            + "costo (si se conoce)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EncargoResponse crear(@Valid @RequestBody EncargoRequest request) {
        return encargoService.crear(request);
    }

    @Operation(summary = "Listar encargos", description = "Filtros: estado, ubicacionId y clienteId")
    @GetMapping
    public PaginaResponse<EncargoResponse> listar(
            @RequestParam(required = false) EstadoEncargo estado,
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(required = false) Long clienteId,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return encargoService.listar(estado, ubicacionId, clienteId, pageable);
    }

    @Operation(summary = "Obtener un encargo")
    @GetMapping("/{id}")
    public EncargoResponse obtener(@PathVariable Long id) {
        return encargoService.obtener(id);
    }

    @Operation(summary = "Entregar encargo", description = "Crea la venta con el precio acordado en la caja abierta "
            + "del usuario y descuenta el stock. 422 si aun no hay stock suficiente")
    @PostMapping("/{id}/entregar")
    public EncargoResponse entregar(@PathVariable Long id, @Valid @RequestBody EntregaEncargoRequest request) {
        return encargoService.entregar(id, request);
    }

    @Operation(summary = "Anular encargo pendiente", description = "No toca el stock")
    @PostMapping("/{id}/anular")
    public EncargoResponse anular(@PathVariable Long id, @Valid @RequestBody AnulacionRequest request) {
        return encargoService.anular(id, request);
    }
}
