package com.ferreteria.service;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.inventario.StockResponse;
import com.ferreteria.dto.reportes.ComprasPorProveedorResponse;
import com.ferreteria.dto.reportes.ProductoVendidoResponse;
import com.ferreteria.dto.reportes.TrasladosPorTiendaResponse;
import com.ferreteria.dto.reportes.VentasPorTiendaResponse;
import com.ferreteria.repository.CompraRepository;
import com.ferreteria.repository.TrasladoRepository;
import com.ferreteria.repository.VentaDetalleRepository;
import com.ferreteria.repository.VentaRepository;
import com.ferreteria.security.AccesoUbicacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Reportes agregados por rango de fechas (dias de Lima). Todos paginados y limitados a la ubicacion del usuario
 * si no es ADMIN. El orden de cada reporte lo fija su consulta (el parametro sort se ignora).
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

    public enum OrdenProductos { MONTO, CANTIDAD }

    private final VentaRepository ventaRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final TrasladoRepository trasladoRepository;
    private final CompraRepository compraRepository;
    private final InventarioService inventarioService;
    private final AccesoUbicacionService accesoUbicacion;
    private final Calendario calendario;

    @Transactional(readOnly = true)
    public PaginaResponse<VentasPorTiendaResponse> ventasPorTienda(LocalDate desde, LocalDate hasta,
                                                                   Long ubicacionId, Pageable pageable) {
        calendario.validarRango(desde, hasta);
        return PaginaResponse.de(ventaRepository.ventasPorTienda(calendario.inicio(desde),
                calendario.finExclusivo(hasta), accesoUbicacion.ubicacionParaConsultar(ubicacionId), sinOrden(pageable)));
    }

    /** El ALMACENERO ve los traslados que salieron de su almacen; el ADMIN puede filtrar por origen. */
    @Transactional(readOnly = true)
    public PaginaResponse<TrasladosPorTiendaResponse> trasladosPorTienda(LocalDate desde, LocalDate hasta,
                                                                         Long origenId, Pageable pageable) {
        calendario.validarRango(desde, hasta);
        return PaginaResponse.de(trasladoRepository.trasladosPorDestino(calendario.inicio(desde),
                calendario.finExclusivo(hasta), accesoUbicacion.ubicacionParaConsultar(origenId), sinOrden(pageable)));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ComprasPorProveedorResponse> comprasPorProveedor(LocalDate desde, LocalDate hasta,
                                                                           Long ubicacionId, Long empresaId,
                                                                           Pageable pageable) {
        calendario.validarRango(desde, hasta);
        return PaginaResponse.de(compraRepository.comprasPorProveedor(desde, hasta,
                accesoUbicacion.ubicacionParaConsultar(ubicacionId), empresaId, sinOrden(pageable)));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ProductoVendidoResponse> productosMasVendidos(LocalDate desde, LocalDate hasta,
                                                                        Long ubicacionId, OrdenProductos orden,
                                                                        Pageable pageable) {
        calendario.validarRango(desde, hasta);
        Long ubicacion = accesoUbicacion.ubicacionParaConsultar(ubicacionId);
        var pagina = orden == OrdenProductos.CANTIDAD
                ? ventaDetalleRepository.masVendidosPorCantidad(calendario.inicio(desde),
                calendario.finExclusivo(hasta), ubicacion, sinOrden(pageable))
                : ventaDetalleRepository.masVendidosPorMonto(calendario.inicio(desde),
                calendario.finExclusivo(hasta), ubicacion, sinOrden(pageable));
        return PaginaResponse.de(pagina);
    }

    /** Productos cuyo stock llego al minimo configurado. */
    @Transactional(readOnly = true)
    public PaginaResponse<StockResponse> stockBajo(Long ubicacionId, Pageable pageable) {
        return inventarioService.listarStock(ubicacionId, null, null, true, pageable);
    }

    private static Pageable sinOrden(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }
}
