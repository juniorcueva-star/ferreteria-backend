package com.ferreteria.controller;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.cotizaciones.*;
import com.ferreteria.entity.enums.EstadoCotizacion;
import com.ferreteria.service.CotizacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/cotizaciones") @RequiredArgsConstructor
public class CotizacionController {
 private final CotizacionService service;
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public CotizacionResponse crear(@Valid @RequestBody CotizacionRequest r){return service.crear(r);}
 @GetMapping public PaginaResponse<CotizacionResponse> listar(@RequestParam(required=false) Long ubicacionId,@RequestParam(required=false) EstadoCotizacion estado,@ParameterObject @PageableDefault(size=20,sort="id",direction=Sort.Direction.DESC) Pageable p){return service.listar(ubicacionId,estado,p);}
 @GetMapping("/{id}") public CotizacionResponse obtener(@PathVariable Long id){return service.obtener(id);}
 @PutMapping("/{id}") public CotizacionResponse actualizar(@PathVariable Long id,@Valid @RequestBody CotizacionRequest r){return service.actualizar(id,r);}
 @PostMapping("/{id}/emitir") public CotizacionResponse emitir(@PathVariable Long id){return service.emitir(id);}
 @PostMapping("/{id}/anular") public CotizacionResponse anular(@PathVariable Long id){return service.anular(id);}
}
