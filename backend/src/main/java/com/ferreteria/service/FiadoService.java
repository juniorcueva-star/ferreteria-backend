package com.ferreteria.service;

import java.time.format.DateTimeFormatter;
import org.springframework.data.domain.Sort;
import com.ferreteria.repository.ClienteRepository;
import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.Cliente;
import com.ferreteria.dto.cotizaciones.EnlaceWhatsappResponse;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.fiado.DeudorResponse;
import com.ferreteria.dto.ventas.AbonoRequest;
import com.ferreteria.dto.ventas.VentaResponse;
import com.ferreteria.entity.CajaSesion;
import com.ferreteria.entity.Pago;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.Venta;
import com.ferreteria.entity.enums.CondicionVenta;
import com.ferreteria.entity.enums.EstadoVenta;
import com.ferreteria.entity.enums.TipoPago;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.Especificaciones;
import com.ferreteria.repository.VentaRepository;
import com.ferreteria.security.AccesoUbicacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ventas al fiado: abonos (pagos parciales posteriores) y consultas de deudas y deudores.
 */
@Service
@RequiredArgsConstructor
public class FiadoService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ClienteRepository clienteRepository;
    private final Whatsapp whatsapp;
    private final VentaRepository ventaRepository;
    private final AccesoUbicacionService accesoUbicacion;
    private final Buscador buscador;
    private final Cobros cobros;
    private final Calendario calendario;

    /**
     * Registra un abono en la caja abierta del usuario. El abono se cobra en la misma tienda que hizo la venta
     * (cada tienda tiene su RUC y su dinero) y no puede superar el saldo pendiente.
     * Bloqueos: primero la venta (para que dos abonos simultaneos no paguen de mas) y luego la caja.
     */
    @Transactional
    public VentaResponse registrarAbono(Long ventaId, AbonoRequest request) {
        Venta venta = ventaRepository.bloquear(ventaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta", ventaId));
        accesoUbicacion.validarAcceso(venta.getUbicacion().getId());
        if (venta.getCondicion() != CondicionVenta.CREDITO || venta.getEstado() != EstadoVenta.EMITIDA) {
            throw new ReglaNegocioException("Solo se abona a ventas al credito vigentes");
        }
        if (venta.getSaldoPendiente().signum() == 0) {
            throw new ReglaNegocioException("La venta " + venta.getNumeroDocumento() + " ya esta pagada");
        }
        CajaSesion caja = cobros.cajaAbiertaDelUsuario();
        if (!caja.getUbicacion().getId().equals(venta.getUbicacion().getId())) {
            throw new ReglaNegocioException("El abono se cobra en la tienda donde se hizo la venta ("
                    + venta.getUbicacion().getNombre() + ")");
        }
        Usuario usuario = buscador.usuarioActual();
        List<Pago> pagos = cobros.crearPagos(request.pagos(), TipoPago.ABONO, caja, usuario);
        BigDecimal abonado = pagos.stream().map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (abonado.compareTo(venta.getSaldoPendiente()) > 0) {
            throw new ReglaNegocioException("El abono (" + abonado.toPlainString() + ") supera el saldo pendiente ("
                    + venta.getSaldoPendiente().toPlainString() + ")");
        }
        pagos.forEach(venta::agregarPago);
        venta.setSaldoPendiente(venta.getSaldoPendiente().subtract(abonado));
        ventaRepository.flush(); // asigna id a los pagos nuevos para la respuesta
        return VentaResponse.completa(venta);
    }

    /**
     * Ventas al fiado con saldo pendiente.
     *
     * @param soloVencidas true = solo las que tienen fecha de vencimiento anterior a hoy
     */
    @Transactional(readOnly = true)
    public PaginaResponse<VentaResponse> listarDeudas(Long ubicacionId, Long clienteId, boolean soloVencidas,
                                                      Pageable pageable) {
        Specification<Venta> filtro = Specification.allOf(
                Especificaciones.igual("ubicacion.id", accesoUbicacion.ubicacionParaConsultar(ubicacionId)),
                Especificaciones.igual("cliente.id", clienteId),
                Especificaciones.igual("estado", EstadoVenta.EMITIDA),
                (root, query, cb) -> cb.greaterThan(root.get("saldoPendiente"), BigDecimal.ZERO),
                soloVencidas ? Especificaciones.antesDe("fechaVencimiento", calendario.hoy())
                        : Specification.unrestricted());
        return PaginaResponse.de(ventaRepository.findAll(filtro,
                Ordenamiento.validar(pageable, "id", "fecha", "fechaVencimiento", "saldoPendiente", "total")), VentaResponse::resumen);
    }

    /** Clientes que deben, ordenados de mayor a menor deuda. */
    @Transactional(readOnly = true)
    public PaginaResponse<DeudorResponse> listarDeudores(Long ubicacionId, Pageable pageable) {
        Long ubicacion = accesoUbicacion.ubicacionParaConsultar(ubicacionId);
        Pageable sinOrden = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()); // el orden lo fija la consulta
        return PaginaResponse.de(ventaRepository.deudores(ubicacion, sinOrden));
    }

    /**
     * Recordatorio de deuda por WhatsApp: lista las ventas al credito con saldo del cliente (las de su tienda si es
     * VENDEDOR), con su vencimiento y el total. Sin telefono usa el celular del cliente.
     */
    @Transactional(readOnly = true)
    public EnlaceWhatsappResponse recordatorioWhatsapp(Long clienteId, String telefono) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", clienteId));
        List<VentaResponse> deudas = listarDeudas(null, clienteId, false,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "fecha"))).contenido();
        if (deudas.isEmpty()) {
            throw new ReglaNegocioException(cliente.getNombre() + " no tiene deudas pendientes");
        }
        String celular = telefono != null && !telefono.isBlank() ? telefono : cliente.getTelefono();
        if (celular == null || celular.isBlank()) {
            throw new ReglaNegocioException("Indique el celular del cliente");
        }
        Empresa empresa = ventaRepository.getReferenceById(deudas.getFirst().id()).getEmpresa();
        StringBuilder mensaje = new StringBuilder("Hola ").append(cliente.getNombre()).append(", le saludamos de ")
                .append(empresa.getNombreComercial() == null ? empresa.getRazonSocial() : empresa.getNombreComercial())
                .append(". Le recordamos su saldo pendiente:\n");
        BigDecimal total = BigDecimal.ZERO;
        for (VentaResponse v : deudas) {
            mensaje.append("- ").append(v.numeroDocumento()).append(" del ").append(FECHA.format(v.fecha()
                    .atZoneSameInstant(calendario.zona()))).append(": ").append(Whatsapp.soles(v.saldoPendiente()));
            if (v.fechaVencimiento() != null) {
                mensaje.append(v.fechaVencimiento().isBefore(calendario.hoy()) ? " (venció el " : " (vence el ")
                        .append(FECHA.format(v.fechaVencimiento())).append(')');
            }
            mensaje.append('\n');
            total = total.add(v.saldoPendiente());
        }
        mensaje.append("Total: ").append(Whatsapp.soles(total));
        if (empresa.getDatosBancarios() != null) {
            mensaje.append("\nPuede depositar en:\n").append(empresa.getDatosBancarios());
        }
        mensaje.append("\n¡Gracias por su preferencia!");
        return whatsapp.enlace(celular, mensaje.toString());
    }
}
