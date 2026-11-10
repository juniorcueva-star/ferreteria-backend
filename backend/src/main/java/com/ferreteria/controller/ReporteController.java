package com.ferreteria.controller;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.inventario.StockResponse;
import com.ferreteria.dto.reportes.ComprasPorProveedorResponse;
import com.ferreteria.dto.reportes.PedidoSugeridoResponse;
import com.ferreteria.dto.reportes.ProductoVendidoResponse;
import com.ferreteria.dto.reportes.TrasladosPorTiendaResponse;
import com.ferreteria.dto.reportes.ValorInventarioResponse;
import com.ferreteria.dto.reportes.VentasPorTiendaResponse;
import com.ferreteria.service.ReporteAlmacenService;
import com.ferreteria.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "16. Reportes", description = "Reportes por rango de fechas (AAAA-MM-DD, dias de Lima). Quien no es "
        + "ADMIN solo ve su ubicacion")
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteAlmacenService reporteAlmacenService;
    private final ReporteService reporteService;

    @Operation(summary = "Ventas por tienda", description = "Cantidad y montos de ventas (contado, credito, saldo "
            + "pendiente y anuladas) por tienda")
    @GetMapping("/ventas-por-tienda")
    public PaginaResponse<VentasPorTiendaResponse> ventasPorTienda(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long ubicacionId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return reporteService.ventasPorTienda(desde, hasta, ubicacionId, pageable);
    }

    @Operation(summary = "Traslados por tienda destino", description = "Recibidos, en camino y anulados. "
            + "El almacenero ve los que salieron de su almacen")
    @GetMapping("/traslados-por-tienda")
    public PaginaResponse<TrasladosPorTiendaResponse> trasladosPorTienda(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long origenId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return reporteService.trasladosPorTienda(desde, hasta, origenId, pageable);
    }

    @Operation(summary = "Compras por proveedor", description = "Compras registradas por fecha de emision, de mayor "
            + "a menor monto. Filtros: ubicacionId y empresaId")
    @GetMapping("/compras-por-proveedor")
    public PaginaResponse<ComprasPorProveedorResponse> comprasPorProveedor(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(required = false) Long empresaId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return reporteService.comprasPorProveedor(desde, hasta, ubicacionId, empresaId, pageable);
    }

    @Operation(summary = "Productos mas vendidos", description = "orden=MONTO (por defecto) o CANTIDAD (en unidad base)")
    @GetMapping("/productos-mas-vendidos")
    public PaginaResponse<ProductoVendidoResponse> productosMasVendidos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(defaultValue = "MONTO") ReporteService.OrdenProductos orden,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return reporteService.productosMasVendidos(desde, hasta, ubicacionId, orden, pageable);
    }

    @Operation(summary = "Stock bajo", description = "Productos con stock menor o igual a su minimo")
    @GetMapping("/stock-bajo")
    public PaginaResponse<StockResponse> stockBajo(@RequestParam(required = false) Long ubicacionId,
                                                   @ParameterObject @PageableDefault(size = 20, sort = "producto.nombre") Pageable pageable) {
        return reporteService.stockBajo(ubicacionId, pageable);
    }

    @Operation(summary = "Pedido sugerido al proveedor", description = "Productos de la ubicacion (normalmente el "
            + "almacen) que llegaron a su minimo: cuanto comprar (hasta el doble del minimo), ultimo proveedor y ultimo "
            + "costo. ADMIN (indica ubicacionId) y ALMACENERO (su almacen)")
    @GetMapping("/pedido-sugerido")
    public List<PedidoSugeridoResponse> pedidoSugerido(@RequestParam(required = false) Long ubicacionId) {
        return reporteAlmacenService.pedidoSugerido(ubicacionId);
    }

    @Operation(summary = "Valor del inventario y productos sin salida", description = "Cantidad x costo de la ultima "
            + "compra por producto y ubicacion, con los dias desde su ultima salida. diasSinSalida filtra los que no "
            + "salen hace al menos esos dias. ADMIN y ALMACENERO (su almacen)")
    @GetMapping("/valor-inventario")
    public ValorInventarioResponse valorInventario(@RequestParam(required = false) Long ubicacionId,
                                                   @RequestParam(required = false) Integer diasSinSalida) {
        return reporteAlmacenService.valorInventario(ubicacionId, diasSinSalida);
    }
}
