package com.ferreteria.controller;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.inventario.AjusteRequest;
import com.ferreteria.dto.inventario.InventarioInicialRequest;
import com.ferreteria.dto.inventario.KardexResponse;
import com.ferreteria.dto.inventario.StockMinimoRequest;
import com.ferreteria.dto.inventario.StockResponse;
import com.ferreteria.entity.enums.TipoMovimiento;
import com.ferreteria.service.InventarioService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "10. Inventario", description = "Stock por ubicacion, ajustes, inventario inicial y kardex. "
        + "Quien no es ADMIN solo ve su propia ubicacion")
@RestController
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final InventarioService inventarioService;

    @Operation(summary = "Stock por ubicacion", description = "Cantidades en unidad base. Filtros: ubicacionId, "
            + "productoId, texto (codigo o nombre) y soloBajo=true para ver lo que llego al stock minimo")
    @GetMapping("/stock")
    public PaginaResponse<StockResponse> listarStock(
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "false") boolean soloBajo,
            @ParameterObject @PageableDefault(sort = "producto.nombre") Pageable pageable) {
        return inventarioService.listarStock(ubicacionId, productoId, texto, soloBajo, pageable);
    }

    @Operation(summary = "Definir stock minimo de un producto en una ubicacion")
    @PutMapping("/stock/minimo")
    @PreAuthorize("hasRole('ADMIN')")
    public StockResponse definirStockMinimo(@Valid @RequestBody StockMinimoRequest request) {
        return inventarioService.definirStockMinimo(request);
    }

    @Operation(summary = "Registrar ajuste de inventario", description = "ENTRADA o SALIDA manual con motivo "
            + "(merma, rotura, conteo fisico). El almacenero solo ajusta su almacen")
    @PostMapping("/ajustes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'ALMACENERO')")
    public List<KardexResponse> registrarAjuste(@Valid @RequestBody AjusteRequest request) {
        return inventarioService.registrarAjuste(request);
    }

    @Operation(summary = "Cargar inventario inicial", description = "Solo para productos sin movimientos previos "
            + "en esa ubicacion")
    @PostMapping("/inicial")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public List<KardexResponse> registrarInventarioInicial(@Valid @RequestBody InventarioInicialRequest request) {
        return inventarioService.registrarInventarioInicial(request);
    }

    @Operation(summary = "Kardex (historial de movimientos)", description = "Filtros: productoId, ubicacionId, "
            + "tipo, desde, hasta (AAAA-MM-DD, dias de Lima)")
    @GetMapping("/kardex")
    public PaginaResponse<KardexResponse> listarKardex(
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @ParameterObject @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return inventarioService.listarKardex(productoId, ubicacionId, tipo, desde, hasta, pageable);
    }
}
