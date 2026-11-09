package com.ferreteria.controller;

import com.ferreteria.dto.auditoria.AuditoriaResponse;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.entity.enums.EntidadAuditada;
import com.ferreteria.service.AuditoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "21. Auditoria", description = "Quien cambio precios, stock, productos, usuarios o empresas, y cuando. "
        + "Tambien las ventas y compras anuladas. Solo ADMIN")
@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @Operation(summary = "Listar cambios", description = "Filtros: entidad (PRODUCTO, PRECIO, STOCK, VENTA, COMPRA, "
            + "USUARIO, EMPRESA), entidadId (Ej: el id del producto), usuarioId, texto (en la descripcion), desde, "
            + "hasta (dias de Lima). Lo mas reciente primero")
    @GetMapping
    public PaginaResponse<AuditoriaResponse> listar(
            @RequestParam(required = false) EntidadAuditada entidad,
            @RequestParam(required = false) Long entidadId,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return auditoriaService.listar(entidad, entidadId, usuarioId, texto, desde, hasta, pageable);
    }
}
