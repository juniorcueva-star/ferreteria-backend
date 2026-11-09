package com.ferreteria.service;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.inventario.AjusteRequest;
import com.ferreteria.dto.inventario.CantidadEnUbicacion;
import com.ferreteria.dto.inventario.InventarioInicialRequest;
import com.ferreteria.dto.inventario.KardexResponse;
import com.ferreteria.dto.inventario.LineaProductoRequest;
import com.ferreteria.dto.inventario.ResumenStockResponse;
import com.ferreteria.dto.inventario.StockMinimoRequest;
import com.ferreteria.dto.inventario.StockResponse;
import com.ferreteria.entity.MovimientoInventario;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Stock;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.enums.TipoMovimiento;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.Especificaciones;
import com.ferreteria.repository.MovimientoInventarioRepository;
import com.ferreteria.repository.ProductoRepository;
import com.ferreteria.repository.StockRepository;
import com.ferreteria.repository.UbicacionRepository;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.service.stock.LineaMovimiento;
import com.ferreteria.service.stock.MovimientoStockService;
import com.ferreteria.service.stock.OrigenMovimiento;
import com.ferreteria.util.Montos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Consulta de stock por ubicacion, stock minimo, ajustes manuales, inventario inicial y kardex.
 */
@Service
@RequiredArgsConstructor
public class InventarioService {

    private static final int MAXIMO_PRODUCTOS_POR_CONSULTA = 100;

    private final StockRepository stockRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final MovimientoStockService movimientoStock;
    private final AccesoUbicacionService accesoUbicacion;
    private final Buscador buscador;
    private final Calendario calendario;
    private final ProductoRepository productoRepository;
    private final UbicacionRepository ubicacionRepository;
    private final BusquedaProductos busquedaProductos;

    /**
     * @param productoIds varios productos a la vez (Ej: los de una pagina del catalogo), como maximo 100
     * @param soloBajo    true = solo productos con stock menor o igual a su minimo (reporte de stock bajo)
     */
    @Transactional(readOnly = true)
    public PaginaResponse<StockResponse> listarStock(Long ubicacionId, Long productoId, List<Long> productoIds,
                                                     String texto, boolean soloBajo, Pageable pageable) {
        validarCantidadDeProductos(productoIds);
        Specification<Stock> filtro = Specification.allOf(
                Especificaciones.igual("ubicacion.id", accesoUbicacion.ubicacionParaConsultar(ubicacionId)),
                Especificaciones.igual("producto.id", productoId),
                Especificaciones.en("producto.id", productoIds),
                Especificaciones.contiene(texto, "producto.codigo", "producto.nombre"),
                Especificaciones.igual("producto.activo", true),
                soloBajo ? stockBajo() : Specification.unrestricted());
        return PaginaResponse.de(stockRepository.findAll(filtro, Ordenamiento.validar(pageable,
                "producto.nombre", "producto.codigo", "cantidad", "stockMinimo", "ubicacion.nombre")), StockResponse::desde);
    }

    /**
     * Disponibilidad de cada producto en todas las tiendas y el almacen activos, con su total (punto 1 del
     * encargado: el vendedor ve el stock de otras ubicaciones en solo lectura para atender pedidos grandes).
     * Lectura para cualquier usuario autenticado; no cambia quien puede vender, trasladar o ajustar.
     * Son tres consultas fijas: la pagina de productos, las ubicaciones activas y el stock de esos productos en todas
     * las ubicaciones (una sola consulta agregada, nunca una por ubicacion).
     */
    @Transactional(readOnly = true)
    public PaginaResponse<ResumenStockResponse> resumirStock(List<Long> productoIds, String texto, Long categoriaId,
                                                            Pageable pageable) {
        validarCantidadDeProductos(productoIds);
        Specification<Producto> filtro = Specification.allOf(
                Especificaciones.en("id", productoIds),
                busquedaProductos.coincidenPalabras(texto),
                Especificaciones.igual("categoria.id", categoriaId),
                Especificaciones.igual("activo", true));
        Page<Producto> productos = productoRepository.findAll(filtro,
                Ordenamiento.validar(pageable, "nombre", "codigo"));
        List<Ubicacion> ubicaciones = ubicacionRepository.findByActivoTrueOrderByTipoDescIdAsc();
        Map<String, CantidadEnUbicacion> cantidades = new HashMap<>();
        if (productos.hasContent()) {
            stockRepository.cantidadesPorUbicacion(productos.map(Producto::getId).getContent())
                    .forEach(c -> cantidades.put(c.productoId() + "-" + c.ubicacionId(), c));
        }
        return PaginaResponse.de(productos, p -> resumen(p, ubicaciones, cantidades));
    }

    private static ResumenStockResponse resumen(Producto producto, List<Ubicacion> ubicaciones,
                                                Map<String, CantidadEnUbicacion> cantidades) {
        List<ResumenStockResponse.StockUbicacion> porUbicacion = ubicaciones.stream().map(u -> {
            CantidadEnUbicacion c = cantidades.get(producto.getId() + "-" + u.getId());
            BigDecimal cantidad = c == null ? Montos.cantidad(BigDecimal.ZERO) : c.cantidad();
            BigDecimal minimo = c == null ? Montos.cantidad(BigDecimal.ZERO) : c.stockMinimo();
            boolean bajo = minimo.signum() > 0 && cantidad.compareTo(minimo) <= 0;
            return new ResumenStockResponse.StockUbicacion(u.getId(), u.getNombre(), u.getTipo(), cantidad, minimo, bajo);
        }).toList();
        BigDecimal total = porUbicacion.stream().map(ResumenStockResponse.StockUbicacion::cantidad)
                .reduce(Montos.cantidad(BigDecimal.ZERO), BigDecimal::add);
        return new ResumenStockResponse(producto.getId(), producto.getCodigo(), producto.getNombre(),
                producto.getUnidadBase(), producto.getCategoria().getNombre(), porUbicacion, total);
    }

    @Transactional
    public StockResponse definirStockMinimo(StockMinimoRequest request) {
        Producto producto = buscador.productoActivo(request.productoId());
        Ubicacion ubicacion = buscador.ubicacionActiva(request.ubicacionId());
        stockRepository.crearSiNoExiste(producto.getId(), ubicacion.getId());
        Stock stock = stockRepository.bloquear(producto.getId(), ubicacion.getId()).orElseThrow();
        stock.setStockMinimo(Montos.cantidad(request.stockMinimo()));
        return StockResponse.desde(stock);
    }

    @Transactional
    public List<KardexResponse> registrarAjuste(AjusteRequest request) {
        Ubicacion ubicacion = buscador.ubicacionActiva(accesoUbicacion.ubicacionParaOperar(request.ubicacionId()));
        TipoMovimiento tipo = request.tipo() == AjusteRequest.TipoAjuste.ENTRADA
                ? TipoMovimiento.AJUSTE_ENTRADA : TipoMovimiento.AJUSTE_SALIDA;
        List<MovimientoInventario> movimientos = movimientoStock.aplicar(ubicacion, tipo, lineas(request.detalles()),
                OrigenMovimiento.ajuste(buscador.usuarioActual(), request.motivo().trim()));
        return movimientos.stream().map(KardexResponse::desde).toList();
    }

    /** Solo para productos sin ningun movimiento previo en la ubicacion: despues se usan compras o ajustes. */
    @Transactional
    public List<KardexResponse> registrarInventarioInicial(InventarioInicialRequest request) {
        Ubicacion ubicacion = buscador.ubicacionActiva(request.ubicacionId());
        Set<Long> productos = new HashSet<>();
        for (LineaProductoRequest linea : request.detalles()) {
            if (!productos.add(linea.productoId())) {
                throw new ReglaNegocioException("El producto " + linea.productoId() + " esta repetido");
            }
            if (movimientoRepository.existsByProductoIdAndUbicacionId(linea.productoId(), ubicacion.getId())) {
                throw new ReglaNegocioException("El producto " + linea.productoId() + " ya tiene movimientos en "
                        + ubicacion.getNombre() + ": use un ajuste de inventario");
            }
        }
        List<MovimientoInventario> movimientos = movimientoStock.aplicar(ubicacion, TipoMovimiento.INVENTARIO_INICIAL,
                lineas(request.detalles()), OrigenMovimiento.ajuste(buscador.usuarioActual(), "Inventario inicial"));
        return movimientos.stream().map(KardexResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public PaginaResponse<KardexResponse> listarKardex(Long productoId, Long ubicacionId, TipoMovimiento tipo,
                                                       LocalDate desde, LocalDate hasta, Pageable pageable) {
        calendario.validarRango(desde, hasta);
        Specification<MovimientoInventario> filtro = Specification.allOf(
                Especificaciones.igual("producto.id", productoId),
                Especificaciones.igual("ubicacion.id", accesoUbicacion.ubicacionParaConsultar(ubicacionId)),
                Especificaciones.igual("tipo", tipo),
                Especificaciones.desde("fecha", calendario.inicio(desde)),
                Especificaciones.antesDe("fecha", calendario.finExclusivo(hasta)));
        return PaginaResponse.de(movimientoRepository.findAll(filtro, Ordenamiento.validar(pageable, "id", "fecha")), KardexResponse::desde);
    }

    private static void validarCantidadDeProductos(List<Long> productoIds) {
        if (productoIds != null && productoIds.size() > MAXIMO_PRODUCTOS_POR_CONSULTA) {
            throw new ReglaNegocioException("Puede consultar como maximo " + MAXIMO_PRODUCTOS_POR_CONSULTA
                    + " productos a la vez (productoIds)");
        }
    }

    private List<LineaMovimiento> lineas(List<LineaProductoRequest> detalles) {
        return detalles.stream()
                .map(d -> buscador.lineaProducto(d.productoId(), d.presentacionId(), d.cantidad()))
                .map(pc -> LineaMovimiento.de(pc.producto(), pc.cantidadBase()))
                .toList();
    }

    private static Specification<Stock> stockBajo() {
        return (root, query, cb) -> cb.and(
                cb.greaterThan(root.get("stockMinimo"), BigDecimal.ZERO),
                cb.lessThanOrEqualTo(root.get("cantidad"), root.get("stockMinimo")));
    }
}
