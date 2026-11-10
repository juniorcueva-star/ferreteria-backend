package com.ferreteria.service.ia;

import com.ferreteria.dto.catalogo.PresentacionResponse;
import com.ferreteria.dto.catalogo.ProductoResponse;
import com.ferreteria.dto.inventario.ResumenStockResponse;
import com.ferreteria.dto.inventario.StockResponse;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.enums.EstadoSolicitud;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.PresentacionRepository;
import com.ferreteria.service.Calendario;
import com.ferreteria.service.FiadoService;
import com.ferreteria.service.InventarioService;
import com.ferreteria.service.ProductoService;
import com.ferreteria.service.ReglaDecimales;
import com.ferreteria.service.ReporteService;
import com.ferreteria.service.SolicitudReposicionService;
import com.ferreteria.service.ia.ModeloIa.Herramienta;
import com.ferreteria.util.Montos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Herramientas de SOLO LECTURA que el asistente de IA puede usar, segun el rol. Cada una llama a los servicios de
 * siempre, con los permisos del usuario que pregunta (el VENDEDOR ve su tienda, nunca costos ni ganancias). El
 * resultado vuelve al modelo como JSON corto.
 */
@Component
@RequiredArgsConstructor
public class HerramientasAsistente {

    static final String BUSCAR = "buscar_productos";
    static final String PRECIO = "calcular_precio";
    static final String BAJO_MINIMO = "productos_bajo_minimo";
    static final String PEDIDOS = "pedidos_reposicion_pendientes";
    static final String VENTAS = "ventas_por_tienda";
    static final String MAS_VENDIDOS = "productos_mas_vendidos";
    static final String DEUDORES = "deudores";

    private static final Map<String, Object> FECHA = Map.of("type", "string", "description",
            "Fecha en formato AAAA-MM-DD (hora de Lima)");

    private static final Map<String, Herramienta> TODAS = Map.of(
            BUSCAR, new Herramienta(BUSCAR, "Busca productos activos por palabras del nombre o codigo (acepta "
                    + "abreviaturas). Devuelve presentaciones con precio de lista, oferta vigente y precio por mayor, "
                    + "el stock en cada tienda y almacen, y donde esta (pasillo/estante). Maximo 8 resultados.",
                    Map.of("texto", Map.of("type", "string", "description", "Palabras a buscar. Ej: perno 3/8 zincado")),
                    List.of("texto")),
            PRECIO, new Herramienta(PRECIO, "Calcula cuanto cuesta llevar una cantidad de una presentacion: aplica la "
                    + "oferta vigente o el precio por mayor si corresponde. Usa el id de presentacion de "
                    + BUSCAR + ".",
                    Map.of("presentacionId", Map.of("type", "integer"),
                            "cantidad", Map.of("type", "number", "description", "Cantidad en esa presentacion")),
                    List.of("presentacionId", "cantidad")),
            BAJO_MINIMO, new Herramienta(BAJO_MINIMO, "Productos que llegaron a su stock minimo (lo que falta "
                    + "reponer), con cantidad, minimo y donde estan.", Map.of(), List.of()),
            PEDIDOS, new Herramienta(PEDIDOS, "Pedidos de reposicion PENDIENTES que las tiendas mandaron al almacen.",
                    Map.of(), List.of()),
            VENTAS, new Herramienta(VENTAS, "Ventas por tienda entre dos fechas (inclusive): numero de ventas, total "
                    + "vendido, al contado, a credito, saldo pendiente y anuladas.",
                    Map.of("desde", FECHA, "hasta", FECHA), List.of("desde", "hasta")),
            MAS_VENDIDOS, new Herramienta(MAS_VENDIDOS, "Los 10 productos que mas se vendieron (por monto) entre dos "
                    + "fechas, de todas las tiendas.", Map.of("desde", FECHA, "hasta", FECHA), List.of("desde", "hasta")),
            DEUDORES, new Herramienta(DEUDORES, "Clientes que deben (fiado), de mayor a menor deuda, con su telefono "
                    + "y el proximo vencimiento.", Map.of(), List.of()));

    private static final Map<Rol, List<String>> POR_ROL = Map.of(
            Rol.VENDEDOR, List.of(BUSCAR, PRECIO),
            Rol.ALMACENERO, List.of(BUSCAR, PRECIO, BAJO_MINIMO, PEDIDOS),
            Rol.ADMIN, List.of(BUSCAR, PRECIO, BAJO_MINIMO, PEDIDOS, VENTAS, MAS_VENDIDOS, DEUDORES));

    static {
        // Evita que una herramienta sin definicion pase desapercibida
        POR_ROL.values().forEach(nombres -> nombres.forEach(n -> {
            if (!TODAS.containsKey(n)) {
                throw new IllegalStateException("Herramienta sin definir: " + n);
            }
        }));
    }

    private final ProductoService productoService;
    private final InventarioService inventarioService;
    private final ReporteService reporteService;
    private final SolicitudReposicionService solicitudService;
    private final FiadoService fiadoService;
    private final PresentacionRepository presentacionRepository;
    private final Calendario calendario;
    private final ObjectMapper json;
    private final PlatformTransactionManager transacciones;

    /** Las herramientas que puede usar este rol, en orden. */
    public List<Herramienta> para(Rol rol) {
        return POR_ROL.getOrDefault(rol, List.of()).stream().map(TODAS::get).toList();
    }

    /**
     * Ejecuta una herramienta. Un error de datos (Ej: producto inexistente) vuelve como texto para que el modelo lo
     * explique; una herramienta que no es del rol no se ejecuta.
     */
    public ModeloIa.Resultado ejecutar(Rol rol, ModeloIa.Llamada llamada) {
        if (!POR_ROL.getOrDefault(rol, List.of()).contains(llamada.nombre())) {
            return new ModeloIa.Resultado(llamada.id(), "Esta consulta no esta disponible para su rol", true);
        }
        try {
            Object resultado = switch (llamada.nombre()) {
                case BUSCAR -> buscar(texto(llamada.entrada(), "texto"));
                case PRECIO -> precio(numero(llamada.entrada(), "presentacionId").longValue(),
                        numero(llamada.entrada(), "cantidad"));
                case BAJO_MINIMO -> bajoMinimo();
                case PEDIDOS -> pedidos();
                case VENTAS -> reporteService.ventasPorTienda(fecha(llamada.entrada(), "desde"),
                        fecha(llamada.entrada(), "hasta"), null, PageRequest.of(0, 20)).contenido();
                case MAS_VENDIDOS -> reporteService.productosMasVendidos(fecha(llamada.entrada(), "desde"),
                        fecha(llamada.entrada(), "hasta"), null, ReporteService.OrdenProductos.MONTO,
                        PageRequest.of(0, 10)).contenido();
                case DEUDORES -> fiadoService.listarDeudores(null, PageRequest.of(0, 15)).contenido();
                default -> throw new ReglaNegocioException("Consulta desconocida");
            };
            return new ModeloIa.Resultado(llamada.id(), json.writeValueAsString(resultado), false);
        } catch (ApiException e) {
            return new ModeloIa.Resultado(llamada.id(), e.getMessage(), true);
        }
    }

    private Map<String, Object> buscar(String texto) {
        List<ProductoResponse> productos = productoService.listar(texto, null, null, true, null,
                PageRequest.of(0, 8, Sort.by("nombre"))).contenido();
        if (productos.isEmpty()) {
            return Map.of("productos", List.of(), "nota", "No se encontro nada con \"" + texto + "\": pruebe con "
                    + "otras palabras, un sinonimo o menos palabras");
        }
        List<Long> ids = productos.stream().map(ProductoResponse::id).toList();
        Map<Long, ResumenStockResponse> stock = inventarioService.resumirStock(ids, null, null, PageRequest.of(0, 8))
                .contenido().stream().collect(Collectors.toMap(ResumenStockResponse::productoId, Function.identity()));
        // Donde esta en las ubicaciones que el usuario puede ver (su tienda o almacen; el ADMIN, todas)
        Map<Long, List<StockResponse>> lugares = inventarioService.listarStock(null, null, ids, null, false,
                        PageRequest.of(0, 100)).contenido().stream()
                .filter(s -> s.ubicacionFisica() != null)
                .collect(Collectors.groupingBy(StockResponse::productoId));
        LocalDate hoy = calendario.hoy();
        List<Map<String, Object>> salida = new ArrayList<>();
        for (ProductoResponse p : productos) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("codigo", p.codigo());
            item.put("nombre", p.nombre());
            item.put("categoria", p.categoriaNombre());
            item.put("unidadBase", p.unidadBase());
            item.put("presentaciones", p.presentaciones().stream().filter(PresentacionResponse::activo)
                    .map(pr -> presentacion(pr, hoy)).toList());
            ResumenStockResponse resumen = stock.get(p.id());
            if (resumen != null) {
                item.put("stock", resumen.ubicaciones().stream()
                        .map(u -> Map.of("ubicacion", u.ubicacionNombre(), "cantidad", u.cantidad())).toList());
            }
            List<StockResponse> donde = lugares.getOrDefault(p.id(), List.of());
            if (!donde.isEmpty()) {
                item.put("dondeEsta", donde.stream()
                        .map(s -> Map.of("ubicacion", s.ubicacionNombre(), "lugar", s.ubicacionFisica())).toList());
            }
            salida.add(item);
        }
        return Map.of("productos", salida);
    }

    private static Map<String, Object> presentacion(PresentacionResponse pr, LocalDate hoy) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("presentacionId", pr.id());
        item.put("nombre", pr.nombre());
        item.put("factor", pr.factor());
        item.put("precioLista", pr.precioVenta());
        boolean ofertaVigente = pr.precioOferta() != null && !hoy.isBefore(pr.ofertaDesde())
                && !hoy.isAfter(pr.ofertaHasta()) && pr.precioOferta().compareTo(pr.precioVenta()) < 0;
        if (ofertaVigente) {
            item.put("oferta", Map.of("precio", pr.precioOferta(), "hasta", pr.ofertaHasta().toString()));
        }
        if (!pr.escalas().isEmpty()) {
            item.put("porMayor", pr.escalas().stream()
                    .map(e -> Map.of("desde", e.cantidadMinima(), "precio", e.precio())).toList());
        }
        return item;
    }

    /**
     * Cada herramienta abre su propia transaccion de lectura (la de los servicios, o esta): asi un error de datos
     * se devuelve al modelo sin dejar una transaccion marcada para deshacer.
     */
    private Map<String, Object> precio(long presentacionId, BigDecimal cantidad) {
        TransactionTemplate lectura = new TransactionTemplate(transacciones);
        lectura.setReadOnly(true);
        return lectura.execute(estado -> calcularPrecio(presentacionId, cantidad));
    }

    private Map<String, Object> calcularPrecio(long presentacionId, BigDecimal cantidad) {
        Presentacion presentacion = presentacionRepository.findById(presentacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Presentacion", presentacionId));
        if (!presentacion.isActivo() || !presentacion.getProducto().isActivo()) {
            throw new ReglaNegocioException("Esa presentacion no esta disponible");
        }
        if (cantidad.signum() <= 0) {
            throw new ReglaNegocioException("La cantidad debe ser mayor que 0");
        }
        ReglaDecimales.validar(cantidad, presentacion, presentacion.getProducto());
        Presentacion.PrecioCobrado cobrado = presentacion.precioPara(cantidad, calendario.hoy());
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("producto", presentacion.getProducto().getNombre());
        salida.put("presentacion", presentacion.getNombre());
        salida.put("cantidad", cantidad);
        salida.put("precioLista", presentacion.getPrecioVenta());
        salida.put("precioUnitario", cobrado.precio());
        salida.put("tipoPrecio", cobrado.tipo());
        salida.put("total", Montos.dinero(cantidad.multiply(cobrado.precio())));
        return salida;
    }

    private List<Map<String, Object>> bajoMinimo() {
        return reporteService.stockBajo(null, PageRequest.of(0, 40, Sort.by("producto.nombre"))).contenido().stream()
                .map(s -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("producto", s.productoCodigo() + " " + s.productoNombre());
                    item.put("ubicacion", s.ubicacionNombre());
                    item.put("cantidad", s.cantidad());
                    item.put("minimo", s.stockMinimo());
                    item.put("unidad", s.unidadBase());
                    if (s.ubicacionFisica() != null) {
                        item.put("lugar", s.ubicacionFisica());
                    }
                    return item;
                }).toList();
    }

    private List<Map<String, Object>> pedidos() {
        return solicitudService.listar(EstadoSolicitud.PENDIENTE, null, PageRequest.of(0, 20, Sort.by("id")))
                .contenido().stream()
                .map(s -> Map.<String, Object>of(
                        "codigo", s.codigo(),
                        "tienda", s.ubicacionNombre(),
                        "pedidoPor", s.usuario(),
                        "fecha", s.fecha().toLocalDate().toString(),
                        "productos", s.detalles().stream()
                                .map(d -> Map.of("producto", d.productoNombre(), "cantidad", d.cantidad())).toList()))
                .toList();
    }

    private static String texto(Map<String, Object> entrada, String campo) {
        Object valor = entrada.get(campo);
        if (!(valor instanceof String texto) || texto.isBlank()) {
            throw new ReglaNegocioException("Falta " + campo);
        }
        return texto.strip();
    }

    private static BigDecimal numero(Map<String, Object> entrada, String campo) {
        Object valor = entrada.get(campo);
        try {
            return new BigDecimal(String.valueOf(valor));
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException("El campo " + campo + " debe ser un numero");
        }
    }

    private static LocalDate fecha(Map<String, Object> entrada, String campo) {
        try {
            return LocalDate.parse(texto(entrada, campo));
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException("La fecha " + campo + " debe tener el formato AAAA-MM-DD");
        }
    }
}
