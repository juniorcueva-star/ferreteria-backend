package com.ferreteria.controller;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.inventario.AjusteRequest;
import com.ferreteria.dto.inventario.InventarioInicialRequest;
import com.ferreteria.dto.inventario.KardexResponse;
import com.ferreteria.dto.inventario.ResumenStockResponse;
import com.ferreteria.dto.inventario.ConteoRequest;
import com.ferreteria.dto.inventario.ConteoResponse;
import com.ferreteria.dto.inventario.StockMinimoRequest;
import com.ferreteria.dto.inventario.UbicacionFisicaRequest;
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
            + "productoId, productoIds (varios a la vez, maximo 100), texto (codigo o nombre) y soloBajo=true para "
            + "ver lo que llego al stock minimo")
    @GetMapping("/stock")
    public PaginaResponse<StockResponse> listarStock(
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) List<Long> productoIds,
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "false") boolean soloBajo,
            @ParameterObject @PageableDefault(size = 20, sort = "producto.nombre") Pageable pageable) {
        return inventarioService.listarStock(ubicacionId, productoId, productoIds, texto, soloBajo, pageable);
    }

    @Operation(summary = "Disponibilidad en todas las ubicaciones (solo lectura)", description = "Para cada producto, "
            + "el stock de cada tienda y del almacen activos y su total, en unidad base. Lo puede leer cualquier "
            + "usuario (el vendedor ve las otras tiendas para pedidos grandes) pero no cambia quien vende, traslada o "
            + "ajusta. Filtros: productoIds (hasta 100), texto (busqueda por palabras) y categoriaId")
    @GetMapping("/resumen")
    public PaginaResponse<ResumenStockResponse> resumirStock(
            @RequestParam(required = false) List<Long> productoIds,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Long categoriaId,
            @ParameterObject @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {
        return inventarioService.resumirStock(productoIds, texto, categoriaId, pageable);
    }

    @Operation(summary = "Ubicacion fisica de un producto", description = "Donde esta dentro de la ubicacion (Ej: "
            + "Pasillo 3 - Estante B). El ALMACENERO en su almacen; el ADMIN en cualquiera (indica ubicacionId)")
    @PutMapping("/stock/ubicacion-fisica")
    public StockResponse definirUbicacionFisica(@Valid @RequestBody UbicacionFisicaRequest request) {
        return inventarioService.definirUbicacionFisica(request);
    }

    @Operation(summary = "Inventario fisico", description = "Lo contado de cada producto (unidad base): el sistema "
            + "crea un ajuste de entrada o salida por cada diferencia, con motivo \"Inventario fisico <fecha>\". "
            + "ALMACENERO (su almacen) o ADMIN (indica ubicacionId)")
    @PostMapping("/conteo")
    public ConteoResponse registrarConteo(@Valid @RequestBody ConteoRequest request) {
        return inventarioService.registrarConteo(request);
    }

    @Operation(summary = "Definir stock minimo de un producto en una ubicacion")
    @PutMapping("/stock/minimo")
    public StockResponse definirStockMinimo(@Valid @RequestBody StockMinimoRequest request) {
        return inventarioService.definirStockMinimo(request);
    }

    @Operation(summary = "Registrar ajuste de inventario", description = "ENTRADA o SALIDA manual con motivo "
            + "(merma, rotura, conteo fisico). El almacenero solo ajusta su almacen")
    @PostMapping("/ajustes")
    @ResponseStatus(HttpStatus.CREATED)
    public List<KardexResponse> registrarAjuste(@Valid @RequestBody AjusteRequest request) {
        return inventarioService.registrarAjuste(request);
    }

    @Operation(summary = "Cargar inventario inicial", description = "Solo para productos sin movimientos previos "
            + "en esa ubicacion")
    @PostMapping("/inicial")
    @ResponseStatus(HttpStatus.CREATED)
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
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return inventarioService.listarKardex(productoId, ubicacionId, tipo, desde, hasta, pageable);
    }
}
