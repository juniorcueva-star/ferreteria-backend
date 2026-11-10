package com.ferreteria.controller;

import com.ferreteria.dto.comun.AnulacionRequest;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.reposicion.AtencionSolicitudRequest;
import com.ferreteria.dto.reposicion.SolicitudReposicionRequest;
import com.ferreteria.dto.reposicion.SolicitudReposicionResponse;
import com.ferreteria.dto.reposicion.SugerenciaReposicionResponse;
import com.ferreteria.entity.enums.EstadoSolicitud;
import com.ferreteria.service.SolicitudReposicionService;
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

import java.util.List;

@Tag(name = "22. Pedidos de reposicion", description = "La tienda pide al almacen lo que se le acaba; el almacen lo "
        + "atiende con un traslado o lo rechaza. VENDEDOR: pide y anula (su tienda). ALMACENERO: atiende y rechaza")
@RestController
@RequestMapping("/api/solicitudes-reposicion")
@RequiredArgsConstructor
public class SolicitudReposicionController {

    private final SolicitudReposicionService solicitudService;

    @Operation(summary = "Pedir reposicion", description = "Productos y cantidades en unidad base. El VENDEDOR pide para "
            + "su tienda; el ADMIN indica ubicacionId")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SolicitudReposicionResponse crear(@Valid @RequestBody SolicitudReposicionRequest request) {
        return solicitudService.crear(request);
    }

    @Operation(summary = "Listar pedidos", description = "Filtros: estado (PENDIENTE, ATENDIDA, RECHAZADA, ANULADA) y "
            + "ubicacionId (tienda). El VENDEDOR solo ve los de su tienda")
    @GetMapping
    public PaginaResponse<SolicitudReposicionResponse> listar(
            @RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(required = false) Long ubicacionId,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return solicitudService.listar(estado, ubicacionId, pageable);
    }

    @Operation(summary = "Sugerencias para una tienda", description = "Productos que llegaron a su stock minimo, con "
            + "la cantidad sugerida (hasta el doble del minimo, sin pasar de lo que hay en los almacenes)")
    @GetMapping("/sugerencias")
    public List<SugerenciaReposicionResponse> sugerir(@RequestParam(required = false) Long ubicacionId) {
        return solicitudService.sugerir(ubicacionId);
    }

    @Operation(summary = "Obtener un pedido")
    @GetMapping("/{id}")
    public SolicitudReposicionResponse obtener(@PathVariable Long id) {
        return solicitudService.obtener(id);
    }

    @Operation(summary = "Atender con un traslado", description = "Crea el traslado del almacen a la tienda (se descuenta "
            + "del almacen al enviarlo). Sin detalles se envia lo pedido; con detalles, esas cantidades (0 = no se "
            + "envia). ALMACENERO o ADMIN")
    @PostMapping("/{id}/atender")
    public SolicitudReposicionResponse atender(@PathVariable Long id, @Valid @RequestBody AtencionSolicitudRequest request) {
        return solicitudService.atender(id, request);
    }

    @Operation(summary = "Rechazar un pedido", description = "Con motivo. ALMACENERO o ADMIN")
    @PostMapping("/{id}/rechazar")
    public SolicitudReposicionResponse rechazar(@PathVariable Long id, @Valid @RequestBody AnulacionRequest request) {
        return solicitudService.rechazar(id, request.motivo());
    }

    @Operation(summary = "Anular un pedido pendiente", description = "La tienda que lo pidio o el ADMIN")
    @PostMapping("/{id}/anular")
    public SolicitudReposicionResponse anular(@PathVariable Long id, @Valid @RequestBody AnulacionRequest request) {
        return solicitudService.anular(id, request.motivo());
    }
}
