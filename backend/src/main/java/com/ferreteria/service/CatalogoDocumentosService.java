package com.ferreteria.service;

import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.enums.TipoUbicacion;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.EmpresaRepository;
import com.ferreteria.repository.Especificaciones;
import com.ferreteria.repository.ProductoRepository;
import com.ferreteria.repository.UbicacionRepository;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.service.documentos.EtiquetasPdf;
import com.ferreteria.service.documentos.ListaPreciosPdf;
import com.ferreteria.service.imagen.AlmacenImagenes;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Documentos del catalogo para imprimir o mandar: la lista de precios y las etiquetas de los estantes. */
@Service
@RequiredArgsConstructor
public class CatalogoDocumentosService {

    /** Para que el PDF no sea enorme: una ferreteria grande tiene unos cientos de productos activos */
    static final int MAXIMO_PRODUCTOS = 2000;
    static final int MAXIMO_ETIQUETAS = 480;

    private final ProductoRepository productoRepository;
    private final UbicacionRepository ubicacionRepository;
    private final EmpresaRepository empresaRepository;
    private final AccesoUbicacionService accesoUbicacion;
    private final Buscador buscador;
    private final Calendario calendario;
    private final AlmacenImagenes almacenImagenes;
    private final ListaPreciosPdf listaPreciosPdf;
    private final EtiquetasPdf etiquetasPdf;

    /**
     * Lista de precios de los productos activos (de una categoria o de todas), con fotos si se piden. El membrete
     * es el de la tienda indicada, la del usuario o la primera tienda activa.
     */
    @Transactional(readOnly = true)
    public byte[] listaPrecios(Long categoriaId, Long ubicacionId, boolean conFotos) {
        Specification<Producto> filtro = Specification.allOf(
                Especificaciones.igual("activo", true),
                Especificaciones.igual("categoria.id", categoriaId));
        List<Producto> productos = productoRepository.findAll(filtro, Sort.by("categoria.nombre", "nombre"));
        if (productos.isEmpty()) {
            throw new ReglaNegocioException("No hay productos activos para la lista de precios");
        }
        if (productos.size() > MAXIMO_PRODUCTOS) {
            throw new ReglaNegocioException("Son demasiados productos para un PDF: elija una categoria");
        }
        Map<Long, byte[]> fotos = new HashMap<>();
        if (conFotos) {
            for (Producto p : productos) {
                almacenImagenes.leerParaDocumento(p.getImagenPublicId(), p.getImagenUrl())
                        .ifPresent(bytes -> fotos.put(p.getId(), bytes));
            }
        }
        Ubicacion tienda = tienda(ubicacionId);
        return listaPreciosPdf.generar(empresa(tienda), tienda, productos, fotos, calendario.hoy());
    }

    /**
     * Etiquetas de los productos indicados: de su presentacion principal (o de todas las activas), repetidas
     * segun las copias.
     */
    @Transactional(readOnly = true)
    public byte[] etiquetas(List<Long> productoIds, int copias, boolean todas) {
        if (productoIds == null || productoIds.isEmpty()) {
            throw new ReglaNegocioException("Elija al menos un producto");
        }
        if (copias < 1 || copias > 50) {
            throw new ReglaNegocioException("Las copias van de 1 a 50 por etiqueta");
        }
        Set<Long> ids = Set.copyOf(productoIds);
        Map<Long, Producto> productos = productoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Producto::getId, p -> p));
        List<Presentacion> etiquetas = new ArrayList<>();
        for (Long id : productoIds.stream().distinct().toList()) {
            Producto producto = productos.get(id);
            if (producto == null || !producto.isActivo()) {
                continue;
            }
            List<Presentacion> elegidas = producto.getPresentaciones().stream()
                    .filter(Presentacion::isActivo)
                    .filter(p -> todas || p.isPrincipal())
                    .toList();
            for (Presentacion p : elegidas) {
                for (int i = 0; i < copias; i++) {
                    etiquetas.add(p);
                }
            }
        }
        if (etiquetas.isEmpty()) {
            throw new ReglaNegocioException("Los productos elegidos no estan activos");
        }
        if (etiquetas.size() > MAXIMO_ETIQUETAS) {
            throw new ReglaNegocioException("Son " + etiquetas.size() + " etiquetas: el maximo por vez es "
                    + MAXIMO_ETIQUETAS + " (20 hojas)");
        }
        Empresa empresa = empresa(tienda(null));
        String negocio = empresa.getNombreComercial() != null ? empresa.getNombreComercial() : empresa.getRazonSocial();
        return etiquetasPdf.generar(etiquetas, negocio, calendario.hoy());
    }

    private Ubicacion tienda(Long ubicacionId) {
        if (ubicacionId != null) {
            return buscador.ubicacionActiva(ubicacionId);
        }
        Long propia = accesoUbicacion.usuarioActual().ubicacionId();
        if (propia != null) {
            return buscador.ubicacionActiva(propia);
        }
        return ubicacionRepository.findByActivoTrueOrderByTipoDescIdAsc().stream()
                .filter(u -> u.getTipo() == TipoUbicacion.TIENDA).findFirst()
                .orElseThrow(() -> new ReglaNegocioException("Registre una tienda para imprimir la lista de precios"));
    }

    private Empresa empresa(Ubicacion tienda) {
        if (tienda.getEmpresa() != null) {
            return tienda.getEmpresa();
        }
        return empresaRepository.findAll(Sort.by("id")).stream().filter(Empresa::isActivo).findFirst()
                .orElseThrow(() -> new ReglaNegocioException("Registre una empresa para imprimir"));
    }

    /** Fecha para el nombre del archivo. */
    public LocalDate hoy() {
        return calendario.hoy();
    }
}
