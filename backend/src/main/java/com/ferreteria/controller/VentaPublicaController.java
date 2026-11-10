package com.ferreteria.controller;

import com.ferreteria.service.VentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "23. Notas de venta publicas", description = "Descarga del PDF desde el enlace enviado por WhatsApp, sin "
        + "iniciar sesion (el token no se puede adivinar)")
@RestController
@RequestMapping("/api/publico/ventas")
@RequiredArgsConstructor
public class VentaPublicaController {

    private final VentaService ventaService;

    @Operation(summary = "PDF de una nota de venta por su token publico")
    @SecurityRequirements
    @GetMapping(value = "/{token}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> descargarPdf(@PathVariable String token) {
        return RespuestaPdf.de(ventaService.generarPdfPublico(token), "nota-de-venta.pdf", false);
    }
}
