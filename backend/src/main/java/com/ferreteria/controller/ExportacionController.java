package com.ferreteria.controller;

import com.ferreteria.service.ExportacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Reportes en Excel (.xlsx) para revisar y archivar las cifras del negocio. */
@Tag(name = "20. Reportes en Excel", description = "Ventas, stock y deudores en archivos .xlsx")
@RestController
@RequestMapping("/api/reportes/excel")
@RequiredArgsConstructor
public class ExportacionController {

    static final MediaType EXCEL =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportacionService exportacionService;

    @Operation(summary = "Ventas en Excel", description = "Tres hojas: detalle de ventas, resumen por tienda y "
            + "productos mas vendidos. Sin fechas: desde el inicio del mes hasta hoy. ADMIN y VENDEDOR (su tienda)")
    @GetMapping("/ventas")
    public ResponseEntity<byte[]> ventas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long ubicacionId) {
        LocalDate inicio = exportacionService.desdePorDefecto(desde);
        LocalDate fin = exportacionService.hastaPorDefecto(hasta);
        return excel(exportacionService.ventas(inicio, fin, ubicacionId), "ventas-" + inicio + "-a-" + fin + ".xlsx");
    }

    @Operation(summary = "Stock en Excel", description = "Stock de cada producto por ubicacion con su minimo. "
            + "soloBajo=true deja solo lo que llego al minimo. Todos los roles (cada uno ve lo que puede consultar)")
    @GetMapping("/stock")
    public ResponseEntity<byte[]> stock(@RequestParam(required = false) Long ubicacionId,
                                        @RequestParam(defaultValue = "false") boolean soloBajo) {
        return excel(exportacionService.stock(ubicacionId, soloBajo), soloBajo ? "stock-bajo.xlsx" : "stock.xlsx");
    }

    @Operation(summary = "Deudores en Excel", description = "Dos hojas: deudores (deuda por cliente) y ventas al "
            + "credito con saldo pendiente. ADMIN y VENDEDOR (su tienda)")
    @GetMapping("/deudores")
    public ResponseEntity<byte[]> deudores(@RequestParam(required = false) Long ubicacionId) {
        return excel(exportacionService.deudores(ubicacionId), "deudores.xlsx");
    }

    private static ResponseEntity<byte[]> excel(byte[] contenido, String nombre) {
        return ResponseEntity.ok()
                .contentType(EXCEL)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nombre).build().toString())
                .body(contenido);
    }
}
