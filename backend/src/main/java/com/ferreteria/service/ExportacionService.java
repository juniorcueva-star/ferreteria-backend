package com.ferreteria.service;

import com.ferreteria.dto.fiado.DeudorResponse;
import com.ferreteria.dto.inventario.StockResponse;
import com.ferreteria.dto.reportes.PedidoSugeridoResponse;
import com.ferreteria.dto.reportes.ProductoVendidoResponse;
import com.ferreteria.dto.reportes.ValorInventarioResponse;
import com.ferreteria.dto.reportes.VentasPorTiendaResponse;
import com.ferreteria.dto.ventas.VentaResponse;
import com.ferreteria.entity.enums.CondicionVenta;
import com.ferreteria.entity.enums.EstadoVenta;
import com.ferreteria.service.documentos.LibroExcel;
import com.ferreteria.service.documentos.LibroExcel.Columna;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.ferreteria.service.documentos.LibroExcel.Tipo.CANTIDAD;
import static com.ferreteria.service.documentos.LibroExcel.Tipo.ENTERO;
import static com.ferreteria.service.documentos.LibroExcel.Tipo.FECHA;
import static com.ferreteria.service.documentos.LibroExcel.Tipo.FECHA_HORA;
import static com.ferreteria.service.documentos.LibroExcel.Tipo.SOLES;
import static com.ferreteria.service.documentos.LibroExcel.Tipo.TEXTO;

/**
 * Reportes en Excel para que el dueno revise y archive las cifras: ventas (detalle, resumen por tienda y productos mas
 * vendidos), stock por ubicacion y deudores. Reutiliza las mismas consultas de los reportes en pantalla, asi que
 * respeta la seguridad por tienda: un VENDEDOR solo exporta lo de su tienda.
 */
@Service
@RequiredArgsConstructor
public class ExportacionService {

    /** Filas maximas por hoja: alcanza para varios anos de una ferreteria y evita archivos gigantes. */
    static final int MAXIMO_FILAS = 10_000;
    private static final DateTimeFormatter FECHA_TEXTO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final VentaService ventaService;
    private final ReporteService reporteService;
    private final InventarioService inventarioService;
    private final FiadoService fiadoService;
    private final ReporteAlmacenService reporteAlmacenService;
    private final Calendario calendario;

    /** Rango por defecto: desde el inicio del mes hasta hoy (hora de Lima). */
    public LocalDate desdePorDefecto(LocalDate desde) {
        return desde != null ? desde : calendario.hoy().withDayOfMonth(1);
    }

    public LocalDate hastaPorDefecto(LocalDate hasta) {
        return hasta != null ? hasta : calendario.hoy();
    }

    @Transactional(readOnly = true)
    public byte[] ventas(LocalDate desde, LocalDate hasta, Long ubicacionId) {
        calendario.validarRango(desde, hasta);
        String rango = "Del " + FECHA_TEXTO.format(desde) + " al " + FECHA_TEXTO.format(hasta) + generado();
        List<VentaResponse> ventas = ventaService.listar(ubicacionId, null, null, null, null, null, null, desde, hasta,
                PageRequest.of(0, MAXIMO_FILAS, Sort.by(Sort.Direction.ASC, "fecha"))).contenido();
        List<VentasPorTiendaResponse> tiendas = reporteService.ventasPorTienda(desde, hasta, ubicacionId,
                PageRequest.of(0, 100)).contenido();
        List<ProductoVendidoResponse> productos = reporteService.productosMasVendidos(desde, hasta, ubicacionId,
                ReporteService.OrdenProductos.MONTO, PageRequest.of(0, MAXIMO_FILAS)).contenido();

        try (LibroExcel libro = new LibroExcel()) {
            libro.hoja("Ventas", "Ventas", rango, List.of(
                    new Columna("Fecha", FECHA_HORA, 17), new Columna("Nota de venta", TEXTO, 15),
                    new Columna("Tienda", TEXTO, 18), new Columna("Vendedor", TEXTO, 24),
                    new Columna("Cliente", TEXTO, 30), new Columna("Condición", TEXTO, 10),
                    new Columna("Estado", TEXTO, 10), new Columna("Op. gravada", SOLES, 13),
                    new Columna("IGV", SOLES, 12), new Columna("Descuento", SOLES, 12),
                    new Columna("Total", SOLES, 13), new Columna("Saldo pendiente", SOLES, 15)),
                    ventas.stream().map(v -> Arrays.<Object>asList(v.fecha(), v.numeroDocumento(), v.ubicacionNombre(),
                            v.vendedor(), v.clienteNombre() == null ? "Cliente varios" : v.clienteNombre(),
                            v.condicion() == CondicionVenta.CONTADO ? "Contado" : "Crédito",
                            v.estado() == EstadoVenta.EMITIDA ? "Emitida" : "Anulada", v.subtotal(), v.igv(),
                            v.descuento(), v.total(), v.saldoPendiente())).toList());
            libro.hoja("Por tienda", "Ventas por tienda (sin anuladas)", rango, List.of(
                    new Columna("Tienda", TEXTO, 20), new Columna("RUC", TEXTO, 13),
                    new Columna("N.° ventas", ENTERO, 11), new Columna("Total vendido", SOLES, 15),
                    new Columna("Al contado", SOLES, 14), new Columna("Al crédito", SOLES, 14),
                    new Columna("Saldo pendiente", SOLES, 15), new Columna("Anuladas", ENTERO, 10)),
                    tiendas.stream().map(t -> Arrays.<Object>asList(t.ubicacionNombre(), t.empresaRuc(), t.cantidadVentas(),
                            t.totalVendido(), t.totalContado(), t.totalCredito(), t.saldoPendiente(),
                            t.cantidadAnuladas())).toList());
            libro.hoja("Productos", "Productos más vendidos (por monto)", rango, List.of(
                    new Columna("Código", TEXTO, 12), new Columna("Producto", TEXTO, 36),
                    new Columna("Unidad", TEXTO, 10), new Columna("Cantidad vendida", CANTIDAD, 16),
                    new Columna("Monto vendido", SOLES, 15), new Columna("N.° ventas", ENTERO, 11)),
                    productos.stream().map(p -> Arrays.<Object>asList(p.productoCodigo(), p.productoNombre(),
                            unidad(p.unidadBase().name()), p.cantidadVendida(), p.montoVendido(),
                            p.numeroVentas())).toList());
            return libro.aBytes();
        }
    }

    @Transactional(readOnly = true)
    public byte[] stock(Long ubicacionId, boolean soloBajo) {
        List<StockResponse> stock = inventarioService.listarStock(ubicacionId, null, null, null, soloBajo,
                PageRequest.of(0, MAXIMO_FILAS, Sort.by("producto.nombre", "ubicacion.nombre"))).contenido();
        try (LibroExcel libro = new LibroExcel()) {
            libro.hoja("Stock", soloBajo ? "Productos con stock bajo" : "Stock por ubicación",
                    "Al " + FECHA_TEXTO.format(calendario.hoy()) + generado(), List.of(
                            new Columna("Código", TEXTO, 12), new Columna("Producto", TEXTO, 36),
                            new Columna("Unidad", TEXTO, 10), new Columna("Ubicación", TEXTO, 18),
                            new Columna("Cantidad", CANTIDAD, 12), new Columna("Stock mínimo", CANTIDAD, 13),
                            new Columna("Estado", TEXTO, 12)),
                    stock.stream().map(s -> Arrays.<Object>asList(s.productoCodigo(), s.productoNombre(),
                            unidad(s.unidadBase().name()), s.ubicacionNombre(), s.cantidad(), s.stockMinimo(),
                            s.bajo() ? "Stock bajo" : "Normal")).toList());
            return libro.aBytes();
        }
    }

    @Transactional(readOnly = true)
    public byte[] deudores(Long ubicacionId) {
        List<DeudorResponse> deudores = fiadoService.listarDeudores(ubicacionId,
                PageRequest.of(0, MAXIMO_FILAS)).contenido();
        List<VentaResponse> deudas = fiadoService.listarDeudas(ubicacionId, null, false,
                PageRequest.of(0, MAXIMO_FILAS, Sort.by(Sort.Direction.ASC, "fecha"))).contenido();
        String fecha = "Al " + FECHA_TEXTO.format(calendario.hoy()) + generado();
        try (LibroExcel libro = new LibroExcel()) {
            libro.hoja("Deudores", "Deudores (fiado)", fecha, List.of(
                    new Columna("Cliente", TEXTO, 32), new Columna("Documento", TEXTO, 16),
                    new Columna("Teléfono", TEXTO, 14), new Columna("N.° ventas", ENTERO, 11),
                    new Columna("Deuda total", SOLES, 14), new Columna("Deuda más antigua", FECHA_HORA, 18),
                    new Columna("Próximo vencimiento", FECHA, 19)),
                    deudores.stream().map(d -> Arrays.<Object>asList(d.clienteNombre(),
                            d.numeroDocumento() == null ? null : d.tipoDocumento() + " " + d.numeroDocumento(),
                            d.telefono(), d.cantidadVentas(), d.deudaTotal(), d.deudaMasAntigua(),
                            d.proximoVencimiento())).toList());
            libro.hoja("Deudas", "Ventas al crédito con saldo pendiente", fecha, List.of(
                    new Columna("Fecha", FECHA_HORA, 17), new Columna("Nota de venta", TEXTO, 15),
                    new Columna("Tienda", TEXTO, 18), new Columna("Cliente", TEXTO, 30),
                    new Columna("Vence", FECHA, 12), new Columna("Total", SOLES, 13),
                    new Columna("Saldo pendiente", SOLES, 15)),
                    deudas.stream().map(v -> Arrays.<Object>asList(v.fecha(), v.numeroDocumento(),
                            v.ubicacionNombre(), v.clienteNombre(), v.fechaVencimiento(), v.total(),
                            v.saldoPendiente())).toList());
            return libro.aBytes();
        }
    }

    @Transactional(readOnly = true)
    public byte[] pedidoSugerido(Long ubicacionId) {
        List<PedidoSugeridoResponse> filas = reporteAlmacenService.pedidoSugerido(ubicacionId);
        try (LibroExcel libro = new LibroExcel()) {
            libro.hoja("Pedido sugerido", "Pedido sugerido al proveedor (productos en su stock minimo)",
                    "Al " + FECHA_TEXTO.format(calendario.hoy()) + generado(), List.of(
                            new Columna("Código", TEXTO, 12), new Columna("Producto", TEXTO, 36),
                            new Columna("Unidad", TEXTO, 10), new Columna("Stock", CANTIDAD, 10),
                            new Columna("Mínimo", CANTIDAD, 10), new Columna("Comprar", CANTIDAD, 11),
                            new Columna("Último proveedor", TEXTO, 34), new Columna("Última compra", FECHA, 14),
                            new Columna("Último costo", SOLES, 13), new Columna("Monto estimado", SOLES, 15)),
                    filas.stream().map(f -> Arrays.<Object>asList(f.productoCodigo(), f.productoNombre(),
                            unidad(f.unidadBase().name()), f.stock(), f.stockMinimo(), f.cantidadSugerida(),
                            f.ultimoProveedor(), f.fechaUltimaCompra(), f.ultimoCosto(), f.montoEstimado())).toList());
            return libro.aBytes();
        }
    }

    @Transactional(readOnly = true)
    public byte[] valorInventario(Long ubicacionId, Integer diasSinSalida) {
        ValorInventarioResponse valor = reporteAlmacenService.valorInventario(ubicacionId, diasSinSalida);
        try (LibroExcel libro = new LibroExcel()) {
            libro.hoja("Valor del inventario", diasSinSalida == null ? "Valor del inventario"
                            : "Productos sin salida hace " + diasSinSalida + " dias o mas",
                    "Total: " + LibroExcel.soles(valor.valorTotal()) + " · " + valor.productosSinCosto()
                            + " sin costo conocido" + generado(), List.of(
                            new Columna("Código", TEXTO, 12), new Columna("Producto", TEXTO, 36),
                            new Columna("Ubicación", TEXTO, 18), new Columna("Cantidad", CANTIDAD, 11),
                            new Columna("Unidad", TEXTO, 10), new Columna("Costo unitario", SOLES, 14),
                            new Columna("Valor", SOLES, 14), new Columna("Última salida", FECHA_HORA, 17),
                            new Columna("Días sin salida", ENTERO, 15)),
                    valor.filas().stream().map(f -> Arrays.<Object>asList(f.productoCodigo(), f.productoNombre(),
                            f.ubicacionNombre(), f.cantidad(), unidad(f.unidadBase().name()), f.costoUnitario(),
                            f.valor(), f.ultimaSalida(), f.diasSinSalida())).toList());
            return libro.aBytes();
        }
    }

    private String generado() {
        return " · Generado el " + FECHA_TEXTO.format(calendario.hoy());
    }

    private static String unidad(String unidadBase) {
        return unidadBase.charAt(0) + unidadBase.substring(1).toLowerCase(Locale.ROOT);
    }
}
