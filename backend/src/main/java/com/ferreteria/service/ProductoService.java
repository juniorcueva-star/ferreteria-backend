package com.ferreteria.service;

import com.ferreteria.dto.catalogo.PresentacionRequest;
import com.ferreteria.dto.catalogo.ProductoActualizarRequest;
import com.ferreteria.dto.catalogo.ProductoCrearRequest;
import com.ferreteria.dto.catalogo.ProductoResponse;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.entity.Categoria;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
import com.ferreteria.exception.DuplicadoException;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.CategoriaRepository;
import com.ferreteria.repository.Especificaciones;
import com.ferreteria.repository.PresentacionRepository;
import com.ferreteria.repository.ProductoRepository;
import com.ferreteria.service.imagen.AlmacenImagenes;
import com.ferreteria.service.imagen.ImagenGuardada;
import com.ferreteria.service.imagen.TipoImagen;
import com.ferreteria.util.Montos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Productos y sus presentaciones (formas de venta con precio propio) y la foto del producto.
 */
@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final PresentacionRepository presentacionRepository;
    private final CategoriaRepository categoriaRepository;
    private final AlmacenImagenes almacenImagenes;

    @Transactional(readOnly = true)
    public PaginaResponse<ProductoResponse> listar(String texto, Long categoriaId, UnidadBase unidadBase,
                                                   Boolean activo, Pageable pageable) {
        Specification<Producto> filtro = Specification.allOf(
                Especificaciones.contiene(texto, "codigo", "nombre", "marca"),
                Especificaciones.igual("categoria.id", categoriaId),
                Especificaciones.igual("unidadBase", unidadBase),
                Especificaciones.igual("activo", activo));
        return PaginaResponse.de(productoRepository.findAll(filtro, pageable), ProductoResponse::desde);
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id) {
        return ProductoResponse.desde(buscar(id));
    }

    /** Para el lector de codigo de barras del punto de venta. */
    @Transactional(readOnly = true)
    public ProductoResponse obtenerPorCodigoBarras(String codigoBarras) {
        Presentacion presentacion = presentacionRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay un producto con el codigo de barras " + codigoBarras));
        return ProductoResponse.desde(presentacion.getProducto());
    }

    @Transactional
    public ProductoResponse crear(ProductoCrearRequest request) {
        String codigo = request.codigo().trim();
        if (productoRepository.existsByCodigo(codigo)) {
            throw new DuplicadoException("Ya existe un producto con el codigo " + codigo);
        }
        validarPresentacionesNuevas(request.presentaciones(), request.unidadBase());

        Producto producto = new Producto();
        producto.setCodigo(codigo);
        producto.setNombre(request.nombre().trim());
        producto.setDescripcion(request.descripcion());
        producto.setMarca(request.marca());
        producto.setCategoria(buscarCategoria(request.categoriaId()));
        producto.setUnidadBase(request.unidadBase());

        boolean hayPrincipal = request.presentaciones().stream().anyMatch(p -> Boolean.TRUE.equals(p.principal()));
        for (int i = 0; i < request.presentaciones().size(); i++) {
            Presentacion presentacion = new Presentacion();
            copiarPresentacion(request.presentaciones().get(i), presentacion);
            presentacion.setPrincipal(hayPrincipal ? presentacion.isPrincipal() : i == 0);
            producto.agregarPresentacion(presentacion);
        }
        if (producto.getPresentaciones().stream().noneMatch(p -> p.isPrincipal() && p.isActivo())) {
            throw new ReglaNegocioException("La presentacion principal debe estar activa");
        }
        return ProductoResponse.desde(productoRepository.save(producto));
    }

    @Transactional
    public ProductoResponse actualizar(Long id, ProductoActualizarRequest request) {
        Producto producto = buscar(id);
        String codigo = request.codigo().trim();
        if (productoRepository.existsByCodigoAndIdNot(codigo, id)) {
            throw new DuplicadoException("Ya existe un producto con el codigo " + codigo);
        }
        producto.setCodigo(codigo);
        producto.setNombre(request.nombre().trim());
        producto.setDescripcion(request.descripcion());
        producto.setMarca(request.marca());
        producto.setCategoria(buscarCategoria(request.categoriaId()));
        producto.setActivo(request.activo());
        return ProductoResponse.desde(producto);
    }

    @Transactional
    public ProductoResponse agregarPresentacion(Long productoId, PresentacionRequest request) {
        Producto producto = buscar(productoId);
        validarNombreLibre(producto, request.nombre(), null);
        validarCodigoBarrasLibre(request.codigoBarras(), null);
        validarFactor(request.factor(), producto.getUnidadBase());

        Presentacion presentacion = new Presentacion();
        copiarPresentacion(request, presentacion);
        if (presentacion.isPrincipal()) {
            if (!presentacion.isActivo()) {
                throw new ReglaNegocioException("La presentacion principal debe estar activa");
            }
            quitarPrincipalActual(producto);
        }
        producto.agregarPresentacion(presentacion);
        productoRepository.flush();
        return ProductoResponse.desde(producto);
    }

    @Transactional
    public ProductoResponse actualizarPresentacion(Long productoId, Long presentacionId, PresentacionRequest request) {
        Producto producto = buscar(productoId);
        Presentacion presentacion = producto.getPresentaciones().stream()
                .filter(p -> p.getId().equals(presentacionId))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Presentacion", presentacionId));
        validarNombreLibre(producto, request.nombre(), presentacionId);
        validarCodigoBarrasLibre(request.codigoBarras(), presentacionId);
        validarFactor(request.factor(), producto.getUnidadBase());

        boolean eraPrincipal = presentacion.isPrincipal();
        boolean seraPrincipal = request.principal() == null ? eraPrincipal : request.principal();
        boolean seraActiva = request.activo() == null ? presentacion.isActivo() : request.activo();
        if (eraPrincipal && !seraPrincipal) {
            throw new ReglaNegocioException(
                    "Siempre debe haber una presentacion principal: marque otra como principal en su lugar");
        }
        if (seraPrincipal && !seraActiva) {
            throw new ReglaNegocioException("La presentacion principal no se puede desactivar");
        }
        if (seraPrincipal && !eraPrincipal) {
            quitarPrincipalActual(producto);
        }
        copiarPresentacion(request, presentacion);
        presentacion.setPrincipal(seraPrincipal);
        presentacion.setActivo(seraActiva);
        return ProductoResponse.desde(producto);
    }

    @Transactional
    public ProductoResponse actualizarImagen(Long productoId, MultipartFile archivo) {
        Producto producto = buscar(productoId);
        byte[] contenido = leer(archivo);
        TipoImagen tipo = TipoImagen.detectar(contenido)
                .orElseThrow(() -> new ApiException(CodigoError.TIPO_ARCHIVO_NO_SOPORTADO,
                        "La imagen debe ser JPG, PNG o WEBP"));
        String anterior = producto.getImagenPublicId();
        ImagenGuardada imagen = almacenImagenes.guardar(contenido, tipo);
        producto.setImagenUrl(imagen.url());
        producto.setImagenPublicId(imagen.publicId());
        almacenImagenes.eliminar(anterior);
        return ProductoResponse.desde(producto);
    }

    @Transactional
    public ProductoResponse eliminarImagen(Long productoId) {
        Producto producto = buscar(productoId);
        almacenImagenes.eliminar(producto.getImagenPublicId());
        producto.setImagenUrl(null);
        producto.setImagenPublicId(null);
        return ProductoResponse.desde(producto);
    }

    // ---------- reglas ----------

    private void validarPresentacionesNuevas(List<PresentacionRequest> presentaciones, UnidadBase unidadBase) {
        Set<String> nombres = new HashSet<>();
        Set<String> codigos = new HashSet<>();
        long principales = 0;
        for (PresentacionRequest p : presentaciones) {
            if (!nombres.add(p.nombre().trim().toLowerCase(Locale.ROOT))) {
                throw new ReglaNegocioException("Presentacion repetida: " + p.nombre());
            }
            String codigoBarras = normalizarCodigoBarras(p.codigoBarras());
            if (codigoBarras != null && !codigos.add(codigoBarras)) {
                throw new ReglaNegocioException("Codigo de barras repetido: " + codigoBarras);
            }
            validarCodigoBarrasLibre(codigoBarras, null);
            validarFactor(p.factor(), unidadBase);
            if (Boolean.TRUE.equals(p.principal())) {
                principales++;
            }
        }
        if (principales > 1) {
            throw new ReglaNegocioException("Solo una presentacion puede ser la principal");
        }
    }

    /** Si el stock se cuenta por unidades, una presentacion no puede contener fracciones de unidad. */
    private void validarFactor(BigDecimal factor, UnidadBase unidadBase) {
        if (unidadBase == UnidadBase.UNIDAD && factor.stripTrailingZeros().scale() > 0) {
            throw new ReglaNegocioException("Para productos por UNIDAD el factor debe ser un numero entero");
        }
    }

    private void validarNombreLibre(Producto producto, String nombre, Long presentacionId) {
        boolean repetido = producto.getPresentaciones().stream()
                .anyMatch(p -> !p.getId().equals(presentacionId) && p.getNombre().equalsIgnoreCase(nombre.trim()));
        if (repetido) {
            throw new DuplicadoException("El producto ya tiene una presentacion llamada " + nombre);
        }
    }

    private void validarCodigoBarrasLibre(String codigoBarras, Long presentacionId) {
        String codigo = normalizarCodigoBarras(codigoBarras);
        if (codigo == null) {
            return;
        }
        boolean existe = presentacionId == null
                ? presentacionRepository.existsByCodigoBarras(codigo)
                : presentacionRepository.existsByCodigoBarrasAndIdNot(codigo, presentacionId);
        if (existe) {
            throw new DuplicadoException("El codigo de barras " + codigo + " ya esta asignado a otra presentacion");
        }
    }

    /** El indice unico parcial exige que no haya dos principales ni por un instante: se apaga y se guarda primero. */
    private void quitarPrincipalActual(Producto producto) {
        producto.getPresentaciones().forEach(p -> p.setPrincipal(false));
        productoRepository.flush();
    }

    private void copiarPresentacion(PresentacionRequest request, Presentacion presentacion) {
        presentacion.setNombre(request.nombre().trim());
        presentacion.setFactor(Montos.cantidad(request.factor()));
        presentacion.setPrecioVenta(Montos.dinero(request.precioVenta()));
        presentacion.setCodigoBarras(normalizarCodigoBarras(request.codigoBarras()));
        presentacion.setPrincipal(Boolean.TRUE.equals(request.principal()));
        presentacion.setActivo(request.activo() == null || request.activo());
    }

    private static String normalizarCodigoBarras(String codigoBarras) {
        return codigoBarras == null || codigoBarras.isBlank() ? null : codigoBarras.trim();
    }

    private byte[] leer(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ReglaNegocioException("Debe adjuntar una imagen");
        }
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new ApiException(CodigoError.SOLICITUD_INVALIDA, "No se pudo leer la imagen");
        }
    }

    private Categoria buscarCategoria(Long id) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoria", id));
        if (!categoria.isActivo()) {
            throw new ReglaNegocioException("La categoria " + categoria.getNombre() + " esta inactiva");
        }
        return categoria;
    }

    private Producto buscar(Long id) {
        return productoRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Producto", id));
    }
}
