package com.ferreteria.controller;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.inventario.StockResponse;
import com.ferreteria.dto.reportes.ComprasPorProveedorResponse;
import com.ferreteria.dto.reportes.ProductoVendidoResponse;
import com.ferreteria.dto.reportes.TrasladosPorTiendaResponse;
import com.ferreteria.dto.reportes.VentasPorTiendaResponse;
import com.ferreteria.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "16. Reportes", description = "Reportes por rango de fechas (AAAA-MM-DD, dias de Lima). Quien no es "
        + "ADMIN solo ve su ubicacion")
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;

    @Operation(summary = "Ventas por tienda", description = "Cantidad y montos de ventas (contado, credito, saldo "
            + "pendiente y anuladas) por tienda")
    @GetMapping("/ventas-por-tienda")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    public PaginaResponse<VentasPorTiendaResponse> ventasPorTienda(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long ubicacionId,
            @ParameterObject @PageableDefault Pageable pageable) {
        return reporteService.ventasPorTienda(desde, hasta, ubicacionId, pageable);
    }

    @Operation(summary = "Traslados por tienda destino", description = "Recibidos, en camino y anulados. "
            + "El almacenero ve los que salieron de su almacen")
    @GetMapping("/traslados-por-tienda")
    @PreAuthorize("hasAnyRole('ADMIN', 'ALMACENERO')")
    public PaginaResponse<TrasladosPorTiendaResponse> trasladosPorTienda(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long origenId,
            @ParameterObject @PageableDefault Pageable pageable) {
        return reporteService.trasladosPorTienda(desde, hasta, origenId, pageable);
    }

    @Operation(summary = "Compras por proveedor", description = "Compras registradas por fecha de emision, de mayor "
            + "a menor monto. Filtros: ubicacionId y empresaId")
    @GetMapping("/compras-por-proveedor")
    @PreAuthorize("hasAnyRole('ADMIN', 'ALMACENERO')")
    public PaginaResponse<ComprasPorProveedorResponse> comprasPorProveedor(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(required = false) Long empresaId,
            @ParameterObject @PageableDefault Pageable pageable) {
        return reporteService.comprasPorProveedor(desde, hasta, ubicacionId, empresaId, pageable);
    }

    @Operation(summary = "Productos mas vendidos", description = "orden=MONTO (por defecto) o CANTIDAD (en unidad base)")
    @GetMapping("/productos-mas-vendidos")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
    public PaginaResponse<ProductoVendidoResponse> productosMasVendidos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(defaultValue = "MONTO") ReporteService.OrdenProductos orden,
            @ParameterObject @PageableDefault Pageable pageable) {
        return reporteService.productosMasVendidos(desde, hasta, ubicacionId, orden, pageable);
    }

    @Operation(summary = "Stock bajo", description = "Productos con stock menor o igual a su minimo")
    @GetMapping("/stock-bajo")
    public PaginaResponse<StockResponse> stockBajo(@RequestParam(required = false) Long ubicacionId,
                                                   @ParameterObject @PageableDefault(sort = "producto.nombre") Pageable pageable) {
        return reporteService.stockBajo(ubicacionId, pageable);
    }
}
