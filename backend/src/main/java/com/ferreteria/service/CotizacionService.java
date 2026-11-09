package com.ferreteria.service;

import com.ferreteria.dto.comun.AnulacionRequest;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.cotizaciones.ConversionCotizacionRequest;
import com.ferreteria.dto.cotizaciones.CotizacionDetalleRequest;
import com.ferreteria.dto.cotizaciones.CotizacionRequest;
import com.ferreteria.dto.cotizaciones.CotizacionResponse;
import com.ferreteria.dto.cotizaciones.EnlaceWhatsappResponse;
import com.ferreteria.dto.cotizaciones.EnvioCorreoRequest;
import com.ferreteria.dto.ventas.VentaDetalleRequest;
import com.ferreteria.dto.ventas.VentaRequest;
import com.ferreteria.dto.ventas.VentaResponse;
import com.ferreteria.entity.Cliente;
import com.ferreteria.entity.Cotizacion;
import com.ferreteria.entity.CotizacionDetalle;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.enums.EstadoCaja;
import com.ferreteria.entity.enums.EstadoCotizacion;
import com.ferreteria.entity.enums.TipoUbicacion;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.CajaSesionRepository;
import com.ferreteria.repository.ClienteRepository;
import com.ferreteria.repository.CotizacionRepository;
import com.ferreteria.repository.Especificaciones;
import com.ferreteria.repository.PresentacionRepository;
import com.ferreteria.repository.VentaRepository;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.service.documentos.CotizacionPdf;
import com.ferreteria.util.Montos;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cotizaciones (punto 2): se crean en BORRADOR (editables), pasan a EMITIDA al emitirlas o enviarlas al cliente y se
 * pueden convertir una sola vez en nota de venta, con el mismo total. No mueven stock ni dinero hasta convertirse.
 */
@Service
@RequiredArgsConstructor
public class CotizacionService {

    private static final int DIAS_VALIDEZ_POR_DEFECTO = 7;
    private static final int MAX_OBSERVACION = 300;
    private static final int MAX_DESCRIPCION = 200;
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CotizacionRepository cotizacionRepository;
    private final PresentacionRepository presentacionRepository;
    private final ClienteRepository clienteRepository;
    private final VentaRepository ventaRepository;
    private final CajaSesionRepository cajaRepository;
    private final VentaService ventaService;
    private final CorreoService correoService;
    private final CotizacionPdf cotizacionPdf;
    private final AccesoUbicacionService accesoUbicacion;
    private final Buscador buscador;
    private final Calendario calendario;

    @Value("${app.url-publica:}")
    private String urlPublica;

    @Transactional
    public CotizacionResponse crear(CotizacionRequest request) {
        Ubicacion tienda = buscador.ubicacionActiva(accesoUbicacion.ubicacionParaOperar(request.ubicacionId()));
        if (tienda.getTipo() != TipoUbicacion.TIENDA) {
            throw new ReglaNegocioException("Las cotizaciones se emiten en una tienda");
        }
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setCodigo(String.format("COT-%06d", cotizacionRepository.siguienteNumero()));
        cotizacion.setUbicacion(tienda);
        cotizacion.setUsuario(buscador.usuarioActual());
        cotizacion.setTokenPublico(tokenAleatorio());
        copiarDatos(cotizacion, request);
        return CotizacionResponse.completa(cotizacionRepository.save(cotizacion));
    }

    /** Solo una cotizacion en BORRADOR se edita. La tienda no cambia. */
    @Transactional
    public CotizacionResponse actualizar(Long id, CotizacionRequest request) {
        Cotizacion cotizacion = bloquear(id);
        if (cotizacion.getEstado() != EstadoCotizacion.BORRADOR) {
            throw new ReglaNegocioException("Solo se edita una cotizacion en BORRADOR");
        }
        if (request.ubicacionId() != null && !request.ubicacionId().equals(cotizacion.getUbicacion().getId())) {
            throw new ReglaNegocioException("No se puede cambiar la tienda de una cotizacion");
        }
        // Se borran las lineas anteriores antes de insertar las nuevas (una presentacion por cotizacion)
        cotizacion.getDetalles().clear();
        cotizacionRepository.flush();
        copiarDatos(cotizacion, request);
        cotizacionRepository.flush();
        return CotizacionResponse.completa(cotizacion);
    }

    @Transactional
    public CotizacionResponse emitir(Long id) {
        Cotizacion cotizacion = bloquear(id);
        if (cotizacion.getEstado() != EstadoCotizacion.BORRADOR) {
            throw new ReglaNegocioException("Solo se emite una cotizacion en BORRADOR");
        }
        cotizacion.setEstado(EstadoCotizacion.EMITIDA);
        return CotizacionResponse.completa(cotizacion);
    }

    /** No se anula una cotizacion ya convertida en venta (se anula la venta). */
    @Transactional
    public CotizacionResponse anular(Long id, AnulacionRequest request) {
        Cotizacion cotizacion = bloquear(id);
        validarNoAnulada(cotizacion);
        if (cotizacion.getVenta() != null) {
            throw new ReglaNegocioException("La cotizacion ya se convirtio en la venta "
                    + cotizacion.getVenta().getNumeroDocumento() + ": anule la venta si corresponde");
        }
        cotizacion.setEstado(EstadoCotizacion.ANULADA);
        String nota = "ANULADA: " + request.motivo().trim();
        String texto = cotizacion.getObservacion() == null ? nota : cotizacion.getObservacion() + " | " + nota;
        cotizacion.setObservacion(texto.length() <= MAX_OBSERVACION ? texto : texto.substring(texto.length() - MAX_OBSERVACION));
        return CotizacionResponse.completa(cotizacion);
    }

    @Transactional(readOnly = true)
    public CotizacionResponse obtener(Long id) {
        return CotizacionResponse.completa(buscarConAcceso(id));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<CotizacionResponse> listar(EstadoCotizacion estado, Long ubicacionId, Long clienteId,
                                                     String texto, Pageable pageable) {
        Specification<Cotizacion> filtro = Specification.allOf(
                Especificaciones.igual("estado", estado),
                Especificaciones.igual("ubicacion.id", accesoUbicacion.ubicacionParaConsultar(ubicacionId)),
                Especificaciones.igual("cliente.id", clienteId),
                Especificaciones.contiene(texto, "codigo", "cliente.nombre"));
        return PaginaResponse.de(cotizacionRepository.findAll(filtro,
                Ordenamiento.validar(pageable, "id", "createdAt", "total", "estado")), CotizacionResponse::resumen);
    }

    /**
     * Convierte la cotizacion en nota de venta (contado o credito, con pagos) en la caja abierta de quien la
     * convierte, con los mismos precios y el mismo total. Descuenta stock y respeta decimales. Solo una vez.
     */
    @Transactional
    public CotizacionResponse convertirEnVenta(Long id, ConversionCotizacionRequest request) {
        Cotizacion cotizacion = bloquear(id);
        validarNoAnulada(cotizacion);
        if (cotizacion.getVenta() != null) {
            throw new ReglaNegocioException("La cotizacion ya se convirtio en la venta "
                    + cotizacion.getVenta().getNumeroDocumento());
        }
        cajaRepository.findByUsuarioIdAndEstado(accesoUbicacion.usuarioActual().id(), EstadoCaja.ABIERTA)
                .filter(caja -> !caja.getUbicacion().getId().equals(cotizacion.getUbicacion().getId()))
                .ifPresent(caja -> {
                    throw new ReglaNegocioException("La cotizacion es de " + cotizacion.getUbicacion().getNombre()
                            + ": su caja abierta es de otra tienda");
                });
        Long clienteId = request.clienteId() != null ? request.clienteId()
                : cotizacion.getCliente() == null ? null : cotizacion.getCliente().getId();
        Map<Long, BigDecimal> precios = new LinkedHashMap<>();
        List<VentaDetalleRequest> detalles = cotizacion.getDetalles().stream().map(d -> {
            precios.put(d.getPresentacion().getId(), d.getPrecioUnitario());
            return new VentaDetalleRequest(d.getPresentacion().getId(), d.getCantidad(), d.getDescuento());
        }).toList();
        VentaResponse venta = ventaService.registrarConPrecios(
                new VentaRequest(request.condicion(), clienteId, request.fechaVencimiento(), detalles, request.pagos()),
                precios);
        cotizacion.setVenta(ventaRepository.getReferenceById(venta.id()));
        cotizacion.setEstado(EstadoCotizacion.EMITIDA);
        cotizacionRepository.flush();
        return CotizacionResponse.completa(cotizacion);
    }

    @Transactional(readOnly = true)
    public byte[] generarPdf(Long id) {
        return cotizacionPdf.generar(buscarConAcceso(id));
    }

    /** PDF para el enlace publico (WhatsApp): basta con el token, que no se puede adivinar. */
    @Transactional(readOnly = true)
    public byte[] generarPdfPublico(String token) {
        Cotizacion cotizacion = cotizacionRepository.findByTokenPublico(token)
                .filter(c -> c.getEstado() != EstadoCotizacion.ANULADA)
                .orElseThrow(() -> new RecursoNoEncontradoException("La cotizacion no existe o fue anulada"));
        return cotizacionPdf.generar(cotizacion);
    }

    /** Envia el PDF por correo. Una cotizacion enviada al cliente queda EMITIDA. */
    @Transactional
    public CotizacionResponse enviarCorreo(Long id, EnvioCorreoRequest request) {
        Cotizacion cotizacion = bloquear(id);
        validarNoAnulada(cotizacion);
        String texto = "Estimado(a) cliente:\n\nLe enviamos adjunta la cotización " + cotizacion.getCodigo() + " de "
                + cotizacion.getUbicacion().getEmpresa().getRazonSocial() + " por un total de "
                + soles(cotizacion.getTotal()) + " (incluye IGV), válida hasta el "
                + FECHA.format(cotizacion.getFechaValidez()) + ".\n"
                + (request.mensaje() == null || request.mensaje().isBlank() ? "" : "\n" + request.mensaje().trim() + "\n")
                + "\nAtentamente,\n" + cotizacion.getUbicacion().getNombre();
        correoService.enviarConPdf(request.destinatario().trim(), "Cotización " + cotizacion.getCodigo(), texto,
                cotizacion.getCodigo() + ".pdf", cotizacionPdf.generar(cotizacion));
        marcarEmitida(cotizacion);
        return CotizacionResponse.completa(cotizacion);
    }

    /**
     * Enlace https://wa.me/51XXXXXXXXX?text=... con el resumen y el enlace de descarga del PDF. El telefono se acepta
     * con 9 digitos (celular peruano) o con el codigo de pais 51. La cotizacion queda EMITIDA.
     */
    @Transactional
    public EnlaceWhatsappResponse generarEnlaceWhatsapp(Long id, String telefono) {
        String numero = normalizarTelefono(telefono);
        Cotizacion cotizacion = bloquear(id);
        validarNoAnulada(cotizacion);
        StringBuilder mensaje = new StringBuilder("Hola")
                .append(cotizacion.getCliente() == null ? "" : " " + cotizacion.getCliente().getNombre())
                .append(", le enviamos la cotización ").append(cotizacion.getCodigo()).append(" de ")
                .append(cotizacion.getUbicacion().getEmpresa().getRazonSocial()).append(":\n");
        cotizacion.getDetalles().forEach(d -> mensaje.append("- ").append(d.getCantidad().stripTrailingZeros()
                .toPlainString()).append(" x ").append(d.getDescripcion()).append(": ").append(soles(d.getSubtotal()))
                .append('\n'));
        mensaje.append("Total: ").append(soles(cotizacion.getTotal())).append(" (incluye IGV)\n")
                .append("Válida hasta: ").append(FECHA.format(cotizacion.getFechaValidez())).append('\n')
                .append("Descargue el PDF: ").append(baseUrl()).append("/api/publico/cotizaciones/")
                .append(cotizacion.getTokenPublico()).append("/pdf");
        String texto = mensaje.toString();
        marcarEmitida(cotizacion);
        String codificado = URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
        return new EnlaceWhatsappResponse("https://wa.me/" + numero + "?text=" + codificado, texto);
    }

    // ---------- armado ----------

    private void copiarDatos(Cotizacion cotizacion, CotizacionRequest request) {
        cotizacion.setCliente(request.clienteId() == null ? null : clienteActivo(request.clienteId()));
        cotizacion.setObservacion(request.observacion() == null || request.observacion().isBlank()
                ? null : request.observacion().trim());
        int dias = request.diasValidez() == null ? DIAS_VALIDEZ_POR_DEFECTO : request.diasValidez();
        cotizacion.setFechaValidez(calendario.hoy().plusDays(dias));
        agregarDetalles(cotizacion, request.detalles());
        calcularTotales(cotizacion);
    }

    private void agregarDetalles(Cotizacion cotizacion, List<CotizacionDetalleRequest> detalles) {
        Map<Long, Presentacion> presentaciones = presentacionRepository.findByIdIn(
                        detalles.stream().map(CotizacionDetalleRequest::presentacionId).toList())
                .stream().collect(Collectors.toMap(Presentacion::getId, Function.identity()));
        Set<Long> usadas = new HashSet<>();
        for (CotizacionDetalleRequest d : detalles) {
            if (!usadas.add(d.presentacionId())) {
                throw new ReglaNegocioException("La presentacion " + d.presentacionId()
                        + " esta repetida: sume la cantidad en una sola linea");
            }
            Presentacion presentacion = presentaciones.get(d.presentacionId());
            if (presentacion == null) {
                throw new RecursoNoEncontradoException("Presentacion", d.presentacionId());
            }
            if (!presentacion.isActivo() || !presentacion.getProducto().isActivo()) {
                throw new ReglaNegocioException(presentacion.getProducto().getNombre() + " ("
                        + presentacion.getNombre() + ") no esta disponible");
            }
            ReglaDecimales.validar(d.cantidad(), presentacion, presentacion.getProducto());
            BigDecimal cantidad = Montos.cantidad(d.cantidad());
            BigDecimal precio = Montos.dinero(d.precioUnitario() == null ? presentacion.getPrecioVenta() : d.precioUnitario());
            BigDecimal importe = Montos.dinero(cantidad.multiply(precio));
            BigDecimal descuento = Montos.dinero(d.descuento() == null ? BigDecimal.ZERO : d.descuento());
            if (descuento.compareTo(importe) > 0) {
                throw new ReglaNegocioException("El descuento de " + presentacion.getProducto().getNombre()
                        + " supera el importe de la linea (" + importe.toPlainString() + ")");
            }
            CotizacionDetalle detalle = new CotizacionDetalle();
            detalle.setProducto(presentacion.getProducto());
            detalle.setPresentacion(presentacion);
            String descripcion = presentacion.getProducto().getNombre() + " - " + presentacion.getNombre();
            detalle.setDescripcion(descripcion.length() <= MAX_DESCRIPCION ? descripcion : descripcion.substring(0, MAX_DESCRIPCION));
            detalle.setCantidad(cantidad);
            detalle.setFactor(presentacion.getFactor());
            detalle.setCantidadBase(Montos.cantidad(cantidad.multiply(presentacion.getFactor())));
            detalle.setPrecioUnitario(precio);
            detalle.setDescuento(descuento);
            detalle.setSubtotal(importe.subtract(descuento));
            if (detalle.getCantidadBase().signum() <= 0) {
                throw new ReglaNegocioException("La cantidad de " + descripcion + " es demasiado pequena");
            }
            cotizacion.agregarDetalle(detalle);
        }
    }

    /** Igual que la venta: total = suma de lineas (con IGV); base = total / 1.18; IGV = total - base. */
    private static void calcularTotales(Cotizacion cotizacion) {
        BigDecimal total = cotizacion.getDetalles().stream().map(CotizacionDetalle::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw new ReglaNegocioException("El total de la cotizacion debe ser mayor que 0");
        }
        BigDecimal subtotal = Montos.baseSinIgv(total);
        cotizacion.setTotal(Montos.dinero(total));
        cotizacion.setSubtotal(subtotal);
        cotizacion.setIgv(Montos.dinero(total.subtract(subtotal)));
        cotizacion.setDescuento(Montos.dinero(cotizacion.getDetalles().stream().map(CotizacionDetalle::getDescuento)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    // ---------- reglas y ayudas ----------

    /** Acepta 987654321 o 51987654321 (con espacios o guiones); devuelve 51 + 9 digitos. */
    static String normalizarTelefono(String telefono) {
        String digitos = telefono == null ? "" : telefono.replaceAll("[\\s()+-]", "");
        if (digitos.matches("9\\d{8}")) {
            return "51" + digitos;
        }
        if (digitos.matches("519\\d{8}")) {
            return digitos;
        }
        throw new ReglaNegocioException("Telefono invalido: use un celular peruano de 9 digitos (Ej: 987654321) "
                + "o con el codigo de pais 51");
    }

    private static void marcarEmitida(Cotizacion cotizacion) {
        if (cotizacion.getEstado() == EstadoCotizacion.BORRADOR) {
            cotizacion.setEstado(EstadoCotizacion.EMITIDA);
        }
    }

    private static void validarNoAnulada(Cotizacion cotizacion) {
        if (cotizacion.getEstado() == EstadoCotizacion.ANULADA) {
            throw new ReglaNegocioException("La cotizacion " + cotizacion.getCodigo() + " esta anulada");
        }
    }

    private String baseUrl() {
        if (!urlPublica.isBlank()) {
            return urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
    }

    private static String soles(BigDecimal monto) {
        return String.format(Locale.US, "S/ %,.2f", monto);
    }

    private static String tokenAleatorio() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Cliente clienteActivo(Long id) {
        Cliente cliente = clienteRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
        if (!cliente.isActivo()) {
            throw new ReglaNegocioException("El cliente " + cliente.getNombre() + " esta inactivo");
        }
        return cliente;
    }

    private Cotizacion buscarConAcceso(Long id) {
        Cotizacion cotizacion = cotizacionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cotizacion", id));
        accesoUbicacion.validarAcceso(cotizacion.getUbicacion().getId());
        return cotizacion;
    }

    private Cotizacion bloquear(Long id) {
        Cotizacion cotizacion = cotizacionRepository.bloquear(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cotizacion", id));
        accesoUbicacion.validarAcceso(cotizacion.getUbicacion().getId());
        return cotizacion;
    }
}
