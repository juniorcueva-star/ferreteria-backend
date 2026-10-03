package com.ferreteria.controller;

import com.ferreteria.dto.comun.AnulacionRequest;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.inventario.TrasladoRequest;
import com.ferreteria.dto.inventario.TrasladoResponse;
import com.ferreteria.entity.enums.EstadoTraslado;
import com.ferreteria.service.TrasladoService;
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

@Tag(name = "09. Traslados", description = "Envio de mercaderia del almacen a una tienda (o entre ubicaciones). "
        + "Enviar y anular: ADMIN y ALMACENERO; recibir: quien trabaja en el destino")
@RestController
@RequestMapping("/api/traslados")
@RequiredArgsConstructor
public class TrasladoController {

    private final TrasladoService trasladoService;

    @Operation(summary = "Listar traslados", description = "Un usuario que no es ADMIN solo ve los que salen o "
            + "llegan a su ubicacion. Filtros: ubicacionId (origen o destino), origenId, destinoId, estado, desde, hasta")
    @GetMapping
    public PaginaResponse<TrasladoResponse> listar(
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(required = false) Long origenId,
            @RequestParam(required = false) Long destinoId,
            @RequestParam(required = false) EstadoTraslado estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @ParameterObject @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return trasladoService.listar(ubicacionId, origenId, destinoId, estado, desde, hasta, pageable);
    }

    @Operation(summary = "Obtener un traslado con su detalle")
    @GetMapping("/{id}")
    public TrasladoResponse obtener(@PathVariable Long id) {
        return trasladoService.obtener(id);
    }

    @Operation(summary = "Enviar traslado", description = "Resta el stock del origen. Queda ENVIADO hasta que el "
            + "destino lo reciba")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrasladoResponse enviar(@Valid @RequestBody TrasladoRequest request) {
        return trasladoService.enviar(request);
    }

    @Operation(summary = "Recibir traslado", description = "Suma el stock en el destino")
    @PostMapping("/{id}/recibir")
    public TrasladoResponse recibir(@PathVariable Long id) {
        return trasladoService.recibir(id);
    }

    @Operation(summary = "Anular traslado ENVIADO", description = "Devuelve el stock al origen")
    @PostMapping("/{id}/anular")
    public TrasladoResponse anular(@PathVariable Long id, @Valid @RequestBody AnulacionRequest request) {
        return trasladoService.anular(id, request);
    }
}
