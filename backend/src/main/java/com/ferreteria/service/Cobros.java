package com.ferreteria.service;

import com.ferreteria.dto.ventas.PagoRequest;
import com.ferreteria.entity.CajaSesion;
import com.ferreteria.entity.MetodoPago;
import com.ferreteria.entity.Pago;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.enums.EstadoCaja;
import com.ferreteria.entity.enums.TipoPago;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.CajaSesionRepository;
import com.ferreteria.repository.MetodoPagoRepository;
import com.ferreteria.security.AccesoUbicacionService;
import com.ferreteria.util.Montos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Logica de cobro comun a ventas y abonos de fiado: obtener la caja abierta del usuario (con bloqueo
 * compartido, ver CajaService.cerrar) y convertir los pagos recibidos en registros de pago validados.
 * Es de uso interno entre services.
 */
@Component
@RequiredArgsConstructor
public class Cobros {

    private final CajaSesionRepository cajaRepository;
    private final MetodoPagoRepository metodoPagoRepository;
    private final AccesoUbicacionService accesoUbicacion;

    /** Caja ABIERTA del usuario conectado, bloqueada en modo compartido hasta el fin de la transaccion. */
    public CajaSesion cajaAbiertaDelUsuario() {
        CajaSesion caja = cajaRepository.bloquearCompartidoPorUsuario(accesoUbicacion.usuarioActual().id(),
                        EstadoCaja.ABIERTA)
                .orElseThrow(() -> new ReglaNegocioException("Debe abrir su caja antes de cobrar"));
        // Si al usuario lo cambiaron de tienda con la caja abierta, ya no puede cobrar en la anterior
        accesoUbicacion.validarAcceso(caja.getUbicacion().getId());
        return caja;
    }

    public List<Pago> crearPagos(List<PagoRequest> pagos, TipoPago tipo, CajaSesion caja, Usuario usuario) {
        return pagos.stream().map(p -> crearPago(p, tipo, caja, usuario)).toList();
    }

    private Pago crearPago(PagoRequest request, TipoPago tipo, CajaSesion caja, Usuario usuario) {
        MetodoPago metodo = metodoPagoRepository.findByCodigoIgnoreCase(request.metodoPago().trim())
                .filter(MetodoPago::isActivo)
                .orElseThrow(() -> new ReglaNegocioException("Metodo de pago no valido: " + request.metodoPago()));
        String operacion = request.numeroOperacion() == null || request.numeroOperacion().isBlank()
                ? null : request.numeroOperacion().trim();
        if (metodo.isRequiereReferencia() && operacion == null) {
            throw new ReglaNegocioException("El pago con " + metodo.getNombre() + " requiere el numero de operacion");
        }
        Pago pago = new Pago();
        pago.setMetodoPago(metodo);
        pago.setMonto(Montos.dinero(request.monto()));
        pago.setNumeroOperacion(operacion);
        pago.setTipo(tipo);
        pago.setCajaSesion(caja);
        pago.setUsuario(usuario);
        return pago;
    }
}
