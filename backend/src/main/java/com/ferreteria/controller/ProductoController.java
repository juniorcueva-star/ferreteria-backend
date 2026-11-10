package com.ferreteria.controller;

import com.ferreteria.dto.catalogo.AccionLoteRequest;
import com.ferreteria.dto.catalogo.AccionLoteResponse;
import com.ferreteria.dto.catalogo.CompletoRequest;
import com.ferreteria.dto.catalogo.CompletoResponse;
import com.ferreteria.dto.catalogo.EscalasPrecioRequest;
import com.ferreteria.dto.catalogo.HistorialPrecioResponse;
import com.ferreteria.dto.catalogo.PrecioSugeridoResponse;
import com.ferreteria.dto.catalogo.PresentacionRequest;
import com.ferreteria.dto.catalogo.ProductoActualizarRequest;
import com.ferreteria.dto.catalogo.ProductoCrearRequest;
import com.ferreteria.dto.catalogo.ProductoResponse;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.service.CompletoService;
import com.ferreteria.service.ProductoService;
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

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "06. Productos", description = "Catalogo con presentaciones y foto. Consultar: todos; modificar: ADMIN")
@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final CompletoService completoService;
    private final ProductoService productoService;

    @Operation(summary = "Listar productos", description = "Filtros: texto (palabras del codigo, nombre, marca, tipo o medida; acepta abreviaturas como per o hex), categoriaId, "
            + "unidadBase y activo. Incluye las presentaciones con sus precios")
    @GetMapping
    public PaginaResponse<ProductoResponse> listar(@RequestParam(required = false) String texto,
                                                   @RequestParam(required = false) Long categoriaId,
                                                   @RequestParam(required = false) UnidadBase unidadBase,
                                                   @RequestParam(required = false) Boolean activo,
                                                   @ParameterObject @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {
        return productoService.listar(texto, categoriaId, unidadBase, activo, pageable);
    }

    @Operation(summary = "Productos frecuentes", description = "Los que se venden en mas ventas de los ultimos 30 dias "
            + "en la tienda (el VENDEDOR, la suya), para el punto de venta. limite: hasta 24 (por defecto 12)")
    @GetMapping("/frecuentes")
    public List<ProductoResponse> listarFrecuentes(@RequestParam(required = false) Long ubicacionId,
                                                   @RequestParam(defaultValue = "12") int limite) {
        return productoService.listarFrecuentes(ubicacionId, limite);
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

    @Operation(summary = "Accion en lote", description = "Asignar categoria, activar o desactivar de 1 a 200 "
            + "productos. Todo o nada: si un producto no existe no se cambia ninguno (422). Solo ADMIN")
    @PostMapping("/acciones-lote")
    public AccionLoteResponse aplicarAccionLote(@Valid @RequestBody AccionLoteRequest request) {
        return productoService.aplicarAccionLote(request);
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

    @Operation(summary = "Precio por mayor de una presentacion", description = "Reemplaza las escalas: desde "
            + "cierta cantidad cada una cuesta menos (Ej: desde 100 a S/ 0.40). Lista vacia = sin precio por mayor. "
            + "A mas cantidad, menor precio. Solo ADMIN")
    @PutMapping("/{id}/presentaciones/{presentacionId}/escalas")
    public ProductoResponse actualizarEscalas(@PathVariable Long id, @PathVariable Long presentacionId,
                                              @Valid @RequestBody EscalasPrecioRequest request) {
        return productoService.actualizarEscalas(id, presentacionId, request);
    }

    @Operation(summary = "Crear un completo", description = "El producto junto con otras piezas (Ej: perno + tuerca "
            + "+ arandela) a precio propio. Sin precioVenta se usa la suma de las piezas. Solo ADMIN")
    @PostMapping("/{id}/completos")
    @ResponseStatus(HttpStatus.CREATED)
    public CompletoResponse crearCompleto(@PathVariable Long id, @Valid @RequestBody CompletoRequest request) {
        return completoService.crear(id, request);
    }

    @Operation(summary = "Editar un completo", description = "Nombre, precio, piezas o activo. No se borra: se "
            + "desactiva. Solo ADMIN")
    @PutMapping("/{id}/completos/{completoId}")
    public CompletoResponse actualizarCompleto(@PathVariable Long id, @PathVariable Long completoId,
                                               @Valid @RequestBody CompletoRequest request) {
        return completoService.actualizar(id, completoId, request);
    }

    @Operation(summary = "Precio sugerido de una presentacion", description = "Costo de referencia (ultima compra), "
            + "ganancia, adicional y precio sugerido = redondear_2(costo x (1 + ganancia/100) + adicional). Con "
            + "porcentajeGanancia o montoAdicional calcula una vista previa sin guardar. Solo ADMIN")
    @GetMapping("/{id}/presentaciones/{presentacionId}/precio-sugerido")
    public PrecioSugeridoResponse obtenerPrecioSugerido(@PathVariable Long id, @PathVariable Long presentacionId,
                                                        @RequestParam(required = false) BigDecimal porcentajeGanancia,
                                                        @RequestParam(required = false) BigDecimal montoAdicional) {
        return productoService.obtenerPrecioSugerido(id, presentacionId, porcentajeGanancia, montoAdicional);
    }

    @Operation(summary = "Historial de precios de una presentacion", description = "Cada cambio del precio final "
            + "con precio anterior, nuevo, usuario y fecha (mas reciente primero). Solo ADMIN")
    @GetMapping("/{id}/presentaciones/{presentacionId}/historial-precios")
    public PaginaResponse<HistorialPrecioResponse> listarHistorialPrecios(
            @PathVariable Long id, @PathVariable Long presentacionId,
            @ParameterObject @PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return productoService.listarHistorialPrecios(id, presentacionId, pageable);
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
