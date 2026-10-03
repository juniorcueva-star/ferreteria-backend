package com.ferreteria.controller;

import com.ferreteria.dto.catalogo.PresentacionRequest;
import com.ferreteria.dto.catalogo.ProductoActualizarRequest;
import com.ferreteria.dto.catalogo.ProductoCrearRequest;
import com.ferreteria.dto.catalogo.ProductoResponse;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "06. Productos", description = "Catalogo con presentaciones y foto. Consultar: todos; modificar: ADMIN")
@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @Operation(summary = "Listar productos", description = "Filtros: texto (codigo, nombre o marca), categoriaId, "
            + "unidadBase y activo. Incluye las presentaciones con sus precios")
    @GetMapping
    public PaginaResponse<ProductoResponse> listar(@RequestParam(required = false) String texto,
                                                   @RequestParam(required = false) Long categoriaId,
                                                   @RequestParam(required = false) UnidadBase unidadBase,
                                                   @RequestParam(required = false) Boolean activo,
                                                   @ParameterObject @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {
        return productoService.listar(texto, categoriaId, unidadBase, activo, pageable);
    }

    @Operation(summary = "Obtener un producto con sus presentaciones")
    @GetMapping("/{id}")
    public ProductoResponse obtener(@PathVariable Long id) {
        return productoService.obtener(id);
    }

    @Operation(summary = "Buscar producto por codigo de barras de una presentacion")
    @GetMapping("/codigo-barras/{codigoBarras}")
    public ProductoResponse obtenerPorCodigoBarras(@PathVariable String codigoBarras) {
        return productoService.obtenerPorCodigoBarras(codigoBarras);
    }

    @Operation(summary = "Crear producto con sus presentaciones")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse crear(@Valid @RequestBody ProductoCrearRequest request) {
        return productoService.crear(request);
    }

    @Operation(summary = "Actualizar datos del producto", description = "Para desactivarlo enviar activo=false")
    @PutMapping("/{id}")
    public ProductoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoActualizarRequest request) {
        return productoService.actualizar(id, request);
    }

    @Operation(summary = "Agregar una presentacion al producto")
    @PostMapping("/{id}/presentaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse agregarPresentacion(@PathVariable Long id, @Valid @RequestBody PresentacionRequest request) {
        return productoService.agregarPresentacion(id, request);
    }

    @Operation(summary = "Actualizar una presentacion (precio, factor, principal, activo)")
    @PutMapping("/{id}/presentaciones/{presentacionId}")
    public ProductoResponse actualizarPresentacion(@PathVariable Long id, @PathVariable Long presentacionId,
                                                   @Valid @RequestBody PresentacionRequest request) {
        return productoService.actualizarPresentacion(id, presentacionId, request);
    }

    @Operation(summary = "Subir o reemplazar la foto", description = "JPG, PNG o WEBP de hasta 2 MB. Se guarda en "
            + "Cloudinary si CLOUDINARY_URL esta configurada; si no, en la carpeta local")
    @PostMapping(value = "/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductoResponse actualizarImagen(@PathVariable Long id, @RequestPart("archivo") MultipartFile archivo) {
        return productoService.actualizarImagen(id, archivo);
    }

    @Operation(summary = "Quitar la foto")
    @DeleteMapping("/{id}/imagen")
    public ProductoResponse eliminarImagen(@PathVariable Long id) {
        return productoService.eliminarImagen(id);
    }
}
