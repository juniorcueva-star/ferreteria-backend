package com.ferreteria.service;

import com.ferreteria.dto.comun.AnulacionRequest;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.encargos.EncargoRequest;
import com.ferreteria.dto.encargos.EncargoResponse;
import com.ferreteria.dto.encargos.EntregaEncargoRequest;
import com.ferreteria.dto.ventas.VentaDetalleRequest;
import com.ferreteria.dto.ventas.VentaRequest;
import com.ferreteria.dto.ventas.VentaResponse;
import com.ferreteria.entity.Cliente;
import com.ferreteria.entity.Encargo;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.enums.EstadoCaja;
import com.ferreteria.entity.enums.EstadoEncargo;
import com.ferreteria.entity.enums.TipoUbicacion;
import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.exception.StockInsuficienteException;
import com.ferreteria.repository.CajaSesionRepository;
import com.ferreteria.repository.ClienteRepository;
import com.ferreteria.repository.EncargoRepository;
import com.ferreteria.repository.Especificaciones;
import com.ferreteria.repository.PresentacionRepository;
import com.ferreteria.repository.VentaRepository;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.util.Montos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Encargos (punto 3): un producto que no hay en stock se encarga con precio propio. Crear o anular un encargo no
 * toca el stock; al entregarlo se crea la venta con el precio acordado y recien ahi se descuenta el stock.
 */
@Service
@RequiredArgsConstructor
public class EncargoService {

    private static final int MAX_OBSERVACION = 300;

    private final EncargoRepository encargoRepository;
    private final PresentacionRepository presentacionRepository;
    private final ClienteRepository clienteRepository;
    private final VentaRepository ventaRepository;
    private final CajaSesionRepository cajaRepository;
    private final VentaService ventaService;
    private final AccesoUbicacionService accesoUbicacion;
    private final Buscador buscador;

    @Transactional
    public EncargoResponse crear(EncargoRequest request) {
        Ubicacion tienda = buscador.ubicacionActiva(accesoUbicacion.ubicacionParaOperar(request.ubicacionId()));
        if (tienda.getTipo() != TipoUbicacion.TIENDA) {
            throw new ReglaNegocioException("Los encargos se registran en una tienda");
        }
        Presentacion presentacion = presentacionRepository.findById(request.presentacionId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Presentacion", request.presentacionId()));
        Producto producto = presentacion.getProducto();
        if (!presentacion.isActivo() || !producto.isActivo()) {
            throw new ReglaNegocioException(producto.getNombre() + " (" + presentacion.getNombre()
                    + ") no esta disponible");
        }
        ReglaDecimales.validar(request.cantidad(), presentacion, producto);
        BigDecimal precio = Montos.dinero(request.precioUnitario());
        BigDecimal costo = request.costoReferencia() != null
                ? Montos.costo(request.costoReferencia()) : presentacion.getCostoReferencia();
        if (costo != null && precio.compareTo(costo) < 0) {
            throw new ReglaNegocioException("El precio del encargo (" + precio.toPlainString()
                    + ") no puede ser menor que su costo (" + costo.toPlainString() + ")");
        }

        Encargo encargo = new Encargo();
        encargo.setCodigo(String.format("EN-%06d", encargoRepository.siguienteNumero()));
        encargo.setUbicacion(tienda);
        encargo.setCliente(request.clienteId() == null ? null : clienteActivo(request.clienteId()));
        encargo.setProducto(producto);
        encargo.setPresentacion(presentacion);
        encargo.setCantidad(Montos.cantidad(request.cantidad()));
        encargo.setPrecioUnitario(precio);
        encargo.setCostoReferencia(costo);
        encargo.setObservacion(textoONulo(request.observacion()));
        encargo.setUsuario(buscador.usuarioActual());
        return EncargoResponse.desde(encargoRepository.save(encargo));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<EncargoResponse> listar(EstadoEncargo estado, Long ubicacionId, Long clienteId,
                                                  Pageable pageable) {
        Specification<Encargo> filtro = Specification.allOf(
                Especificaciones.igual("estado", estado),
                Especificaciones.igual("ubicacion.id", accesoUbicacion.ubicacionParaConsultar(ubicacionId)),
                Especificaciones.igual("cliente.id", clienteId));
        return PaginaResponse.de(encargoRepository.findAll(filtro,
                Ordenamiento.validar(pageable, "id", "createdAt", "estado")), EncargoResponse::desde);
    }

    @Transactional(readOnly = true)
    public EncargoResponse obtener(Long id) {
        Encargo encargo = encargoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Encargo", id));
        accesoUbicacion.validarAcceso(encargo.getUbicacion().getId());
        return EncargoResponse.desde(encargo);
    }

    /**
     * Entrega el encargo: crea la venta (contado o credito, con sus pagos) en la caja abierta de quien entrega, con
     * el precio acordado, y descuenta el stock. Si no hay stock suficiente responde 422 y no cambia nada.
     */
    @Transactional
    public EncargoResponse entregar(Long id, EntregaEncargoRequest request) {
        Encargo encargo = bloquear(id);
        accesoUbicacion.validarAcceso(encargo.getUbicacion().getId());
        validarPendiente(encargo, "entregar");
        cajaRepository.findByUsuarioIdAndEstado(accesoUbicacion.usuarioActual().id(), EstadoCaja.ABIERTA)
                .filter(caja -> !caja.getUbicacion().getId().equals(encargo.getUbicacion().getId()))
                .ifPresent(caja -> {
                    throw new ReglaNegocioException("El encargo se entrega en " + encargo.getUbicacion().getNombre()
                            + ": su caja abierta es de otra tienda");
                });
        Long clienteId = request.clienteId() != null ? request.clienteId()
                : encargo.getCliente() == null ? null : encargo.getCliente().getId();
        VentaRequest venta = new VentaRequest(request.condicion(), clienteId, request.fechaVencimiento(),
                List.of(new VentaDetalleRequest(encargo.getPresentacion().getId(), encargo.getCantidad(), null)),
                request.pagos());
        VentaResponse registrada;
        try {
            registrada = ventaService.registrarConPrecios(venta,
                    Map.of(encargo.getPresentacion().getId(), encargo.getPrecioUnitario()));
        } catch (StockInsuficienteException e) {
            throw new ApiException(CodigoError.REGLA_NEGOCIO, "Aun no hay stock suficiente en "
                    + encargo.getUbicacion().getNombre() + " para entregar el encargo " + encargo.getCodigo()
                    + ": registre la compra o el traslado primero", e.getDetalle());
        }
        encargo.setEstado(EstadoEncargo.ENTREGADO);
        encargo.setVenta(ventaRepository.getReferenceById(registrada.id()));
        encargoRepository.flush();
        return EncargoResponse.desde(encargo);
    }

    /** Solo un encargo PENDIENTE se anula. No toca el stock (nunca lo desconto). */
    @Transactional
    public EncargoResponse anular(Long id, AnulacionRequest request) {
        Encargo encargo = bloquear(id);
        accesoUbicacion.validarAcceso(encargo.getUbicacion().getId());
        validarPendiente(encargo, "anular");
        encargo.setEstado(EstadoEncargo.ANULADO);
        String nota = "ANULADO: " + request.motivo().trim();
        String texto = encargo.getObservacion() == null ? nota : encargo.getObservacion() + " | " + nota;
        encargo.setObservacion(texto.length() <= MAX_OBSERVACION ? texto : texto.substring(texto.length() - MAX_OBSERVACION));
        return EncargoResponse.desde(encargo);
    }

    private static void validarPendiente(Encargo encargo, String accion) {
        if (encargo.getEstado() != EstadoEncargo.PENDIENTE) {
            throw new ReglaNegocioException("No se puede " + accion + " el encargo " + encargo.getCodigo()
                    + " porque esta " + encargo.getEstado());
        }
    }

    private Cliente clienteActivo(Long id) {
        Cliente cliente = clienteRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
        if (!cliente.isActivo()) {
            throw new ReglaNegocioException("El cliente " + cliente.getNombre() + " esta inactivo");
        }
        return cliente;
    }

    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private Encargo bloquear(Long id) {
        return encargoRepository.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Encargo", id));
    }
}
