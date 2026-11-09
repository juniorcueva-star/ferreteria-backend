package com.ferreteria.controller;

import com.ferreteria.service.CotizacionService;
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

@Tag(name = "19. Cotizaciones publicas", description = "Descarga del PDF desde el enlace enviado por WhatsApp, sin "
        + "iniciar sesion (el token no se puede adivinar)")
@RestController
@RequestMapping("/api/publico/cotizaciones")
@RequiredArgsConstructor
public class CotizacionPublicaController {

    private final CotizacionService cotizacionService;

    @Operation(summary = "PDF de una cotizacion por su token publico")
    @SecurityRequirements
    @GetMapping(value = "/{token}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> descargarPdf(@PathVariable String token) {
        return CotizacionController.RespuestaPdf.de(cotizacionService.generarPdfPublico(token), "cotizacion.pdf", false);
    }
}
