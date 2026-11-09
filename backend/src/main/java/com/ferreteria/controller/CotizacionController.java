package com.ferreteria.controller;

import com.ferreteria.dto.comun.AnulacionRequest;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.cotizaciones.ConversionCotizacionRequest;
import com.ferreteria.dto.cotizaciones.CotizacionRequest;
import com.ferreteria.dto.cotizaciones.CotizacionResponse;
import com.ferreteria.dto.cotizaciones.EnlaceWhatsappResponse;
import com.ferreteria.dto.cotizaciones.EnvioCorreoRequest;
import com.ferreteria.entity.enums.EstadoCotizacion;
import com.ferreteria.service.CotizacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "18. Cotizaciones", description = "Cotizaciones con PDF, correo, WhatsApp y conversion en nota de venta. "
        + "ADMIN y VENDEDOR (el vendedor solo en su tienda)")
@RestController
@RequestMapping("/api/cotizaciones")
@RequiredArgsConstructor
public class CotizacionController {

    private final CotizacionService cotizacionService;

    @Operation(summary = "Crear cotizacion", description = "Queda en BORRADOR. Precio por linea editable (si se omite, "
            + "el de lista); valida decimales de cada presentacion")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CotizacionResponse crear(@Valid @RequestBody CotizacionRequest request) {
        return cotizacionService.crear(request);
    }

    @Operation(summary = "Listar cotizaciones", description = "Filtros: estado, ubicacionId, clienteId y texto "
            + "(codigo o cliente)")
    @GetMapping
    public PaginaResponse<CotizacionResponse> listar(
            @RequestParam(required = false) EstadoCotizacion estado,
            @RequestParam(required = false) Long ubicacionId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String texto,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return cotizacionService.listar(estado, ubicacionId, clienteId, texto, pageable);
    }

    @Operation(summary = "Obtener una cotizacion con su detalle")
    @GetMapping("/{id}")
    public CotizacionResponse obtener(@PathVariable Long id) {
        return cotizacionService.obtener(id);
    }

    @Operation(summary = "Actualizar cotizacion (solo BORRADOR)")
    @PutMapping("/{id}")
    public CotizacionResponse actualizar(@PathVariable Long id, @Valid @RequestBody CotizacionRequest request) {
        return cotizacionService.actualizar(id, request);
    }

    @Operation(summary = "Emitir cotizacion", description = "Pasa de BORRADOR a EMITIDA (ya no se edita)")
    @PostMapping("/{id}/emitir")
    public CotizacionResponse emitir(@PathVariable Long id) {
        return cotizacionService.emitir(id);
    }

    @Operation(summary = "Anular cotizacion", description = "No se puede si ya se convirtio en venta")
    @PostMapping("/{id}/anular")
    public CotizacionResponse anular(@PathVariable Long id, @Valid @RequestBody AnulacionRequest request) {
        return cotizacionService.anular(id, request);
    }

    @Operation(summary = "Convertir en nota de venta", description = "Crea la venta con los mismos precios y total, "
            + "en la caja abierta del usuario, y descuenta el stock. Solo una vez")
    @PostMapping("/{id}/convertir-venta")
    public CotizacionResponse convertirEnVenta(@PathVariable Long id,
                                               @Valid @RequestBody ConversionCotizacionRequest request) {
        return cotizacionService.convertirEnVenta(id, request);
    }

    @Operation(summary = "PDF de la cotizacion", description = "descargar=true lo descarga; si no, se muestra para "
            + "imprimir")
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Long id,
                                               @RequestParam(defaultValue = "false") boolean descargar) {
        return RespuestaPdf.de(cotizacionService.generarPdf(id), "cotizacion-" + id + ".pdf", descargar);
    }

    @Operation(summary = "Enviar por correo", description = "Adjunta el PDF. Requiere MAIL_HOST y MAIL_FROM "
            + "configurados; si no, responde 422")
    @PostMapping("/{id}/enviar-correo")
    public CotizacionResponse enviarCorreo(@PathVariable Long id, @Valid @RequestBody EnvioCorreoRequest request) {
        return cotizacionService.enviarCorreo(id, request);
    }

    @Operation(summary = "Enlace de WhatsApp", description = "Devuelve https://wa.me/51XXXXXXXXX?text=... con el "
            + "resumen y el enlace de descarga del PDF")
    @GetMapping("/{id}/enlace-whatsapp")
    public EnlaceWhatsappResponse generarEnlaceWhatsapp(@PathVariable Long id, @RequestParam String telefono) {
        return cotizacionService.generarEnlaceWhatsapp(id, telefono);
    }
}
