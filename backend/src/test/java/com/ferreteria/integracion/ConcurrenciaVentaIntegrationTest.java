package com.ferreteria.integracion;

import com.ferreteria.dto.caja.AperturaCajaRequest;
import com.ferreteria.dto.inventario.AjusteRequest;
import com.ferreteria.dto.inventario.LineaProductoRequest;
import com.ferreteria.dto.inventario.TrasladoRequest;
import com.ferreteria.dto.ventas.PagoRequest;
import com.ferreteria.dto.ventas.VentaDetalleRequest;
import com.ferreteria.dto.ventas.VentaRequest;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.enums.CondicionVenta;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.entity.enums.TipoMovimiento;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.exception.StockInsuficienteException;
import com.ferreteria.repository.MovimientoInventarioRepository;
import com.ferreteria.repository.VentaRepository;
import com.ferreteria.security.UsuarioActual;
import com.ferreteria.security.UsuarioAuthenticationToken;
import com.ferreteria.service.CajaService;
import com.ferreteria.service.InventarioService;
import com.ferreteria.service.TrasladoService;
import com.ferreteria.service.VentaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Operaciones simultaneas sobre el mismo stock. Cada hilo actua como un usuario distinto y todos arrancan a la vez
 * (CountDownLatch). Los bloqueos pesimistas deben impedir vender o sacar mas de lo que hay y dejar stock negativo.
 */
class ConcurrenciaVentaIntegrationTest extends IntegracionTestBase {

    @Autowired private VentaService ventaService;
    @Autowired private CajaService cajaService;
    @Autowired private InventarioService inventarioService;
    @Autowired private TrasladoService trasladoService;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private MovimientoInventarioRepository movimientoRepository;

    @Test
    @DisplayName("Dos ventas simultaneas de 7 con stock 10: solo una pasa y el stock queda en 3")
    void dosVentasSimultaneas() throws Exception {
        Producto tubo = datos.producto("TUB-1", UnidadBase.UNIDAD, "12.00");
        cargarStock(tubo, datos.tienda1, 10);

        List<Resultado> resultados = venderEnParalelo(tubo, 2, 7);

        assertThat(resultados).filteredOn(Resultado::exito).hasSize(1);
        assertThat(resultados).filteredOn(r -> r.error() instanceof StockInsuficienteException).hasSize(1);
        assertThat(datos.stock(tubo, datos.tienda1)).isEqualByComparingTo("3");
        assertThat(ventaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Ocho ventas simultaneas de 1 con stock 5: exactamente 5 pasan, stock 0 y numeros sin repetir")
    void muchasVentasSimultaneas() throws Exception {
        Producto llave = datos.producto("LLA-1", UnidadBase.UNIDAD, "25.00");
        cargarStock(llave, datos.tienda1, 5);

        List<Resultado> resultados = venderEnParalelo(llave, 8, 1);

        assertThat(resultados).filteredOn(Resultado::exito).hasSize(5);
        assertThat(resultados).filteredOn(r -> !r.exito())
                .allMatch(r -> r.error() instanceof StockInsuficienteException);
        assertThat(datos.stock(llave, datos.tienda1)).isEqualByComparingTo("0");
        assertThat(ventaRepository.findAll()).extracting(v -> v.getNumero()).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
        assertThat(movimientoRepository.findAll()).filteredOn(m -> m.getTipo() == TipoMovimiento.VENTA)
                .hasSize(5)
                .allMatch(m -> m.getSaldoResultante().signum() >= 0);
    }

    @Test
    @DisplayName("Salidas simultaneas sin correlativo (ajustes): el bloqueo de la fila de stock evita perder "
            + "actualizaciones")
    void ajustesSimultaneos() throws Exception {
        Producto codo = datos.producto("COD-1", UnidadBase.UNIDAD, "3.00");
        cargarStock(codo, datos.tienda1, 5);
        UsuarioActual admin = new UsuarioActual(datos.admin.getId(), "admin", "Admin", Rol.ADMIN, null);
        AjusteRequest salida = new AjusteRequest(datos.tienda1.getId(), AjusteRequest.TipoAjuste.SALIDA,
                "Prueba de concurrencia", List.of(new LineaProductoRequest(codo.getId(), null, BigDecimal.ONE)));

        List<Resultado> resultados = enParalelo(Collections.nCopies(8, admin),
                () -> inventarioService.registrarAjuste(salida));

        assertThat(resultados).filteredOn(Resultado::exito).hasSize(5);
        assertThat(datos.stock(codo, datos.tienda1)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Una venta y un traslado que compiten por el mismo stock no venden ni envian de mas")
    void ventaContraTraslado() throws Exception {
        Producto valvula = datos.producto("VAL-1", UnidadBase.UNIDAD, "15.00");
        cargarStock(valvula, datos.tienda1, 10);
        Usuario usuario = datos.usuario("vend_traslado", Rol.VENDEDOR, datos.tienda1);
        UsuarioActual vendedor = new UsuarioActual(usuario.getId(), usuario.getUsername(), "V", Rol.VENDEDOR,
                datos.tienda1.getId());
        comoUsuario(vendedor, () -> cajaService.abrir(new AperturaCajaRequest(null, BigDecimal.ZERO)));
        UsuarioActual admin = new UsuarioActual(datos.admin.getId(), "admin", "Admin", Rol.ADMIN, null);
        VentaRequest venta = new VentaRequest(CondicionVenta.CONTADO, null, null,
                List.of(new VentaDetalleRequest(datos.presentacionId(valvula, 0), BigDecimal.valueOf(6), null)),
                List.of(new PagoRequest("EFECTIVO", new BigDecimal("90.00"), null)));
        TrasladoRequest traslado = new TrasladoRequest(datos.tienda1.getId(), datos.tienda2.getId(), null,
                List.of(new LineaProductoRequest(valvula.getId(), null, BigDecimal.valueOf(6))));

        List<Resultado> resultados = enParalelo(List.of(vendedor, admin), List.of(
                () -> ventaService.registrar(venta), () -> trasladoService.enviar(traslado)));

        assertThat(resultados).filteredOn(Resultado::exito).hasSize(1);
        assertThat(datos.stock(valvula, datos.tienda1)).isEqualByComparingTo("4");
    }

    private List<Resultado> enParalelo(List<UsuarioActual> usuarios, Callable<?> accion) throws Exception {
        return enParalelo(usuarios, Collections.nCopies(usuarios.size(), accion));
    }

    /** Ejecuta cada accion con su usuario en un hilo distinto; todas arrancan al mismo tiempo. */
    private List<Resultado> enParalelo(List<UsuarioActual> usuarios, List<? extends Callable<?>> acciones)
            throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(usuarios.size());
        try {
            List<Future<Resultado>> futuros = new ArrayList<>();
            for (int i = 0; i < usuarios.size(); i++) {
                UsuarioActual usuario = usuarios.get(i);
                Callable<?> accion = acciones.get(i);
                futuros.add(executor.submit(() -> {
                    largada.await();
                    try {
                        comoUsuario(usuario, accion);
                        return new Resultado(true, null);
                    } catch (Exception e) {
                        return new Resultado(false, e);
                    }
                }));
            }
            largada.countDown();
            List<Resultado> resultados = new ArrayList<>();
            for (Future<Resultado> futuro : futuros) {
                resultados.add(futuro.get(60, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Resultado> venderEnParalelo(Producto producto, int hilos, int cantidad) throws Exception {
        List<UsuarioActual> vendedores = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            Usuario usuario = datos.usuario("vend_conc_" + i, Rol.VENDEDOR, datos.tienda1);
            UsuarioActual actual = new UsuarioActual(usuario.getId(), usuario.getUsername(), usuario.getNombres(),
                    Rol.VENDEDOR, datos.tienda1.getId());
            comoUsuario(actual, () -> cajaService.abrir(new AperturaCajaRequest(null, BigDecimal.ZERO)));
            vendedores.add(actual);
        }
        Long presentacionId = datos.presentacionId(producto, 0);
        BigDecimal pago = producto.getPresentaciones().getFirst().getPrecioVenta().multiply(BigDecimal.valueOf(cantidad));
        VentaRequest request = new VentaRequest(CondicionVenta.CONTADO, null, null,
                List.of(new VentaDetalleRequest(presentacionId, BigDecimal.valueOf(cantidad), null)),
                List.of(new PagoRequest("EFECTIVO", pago, null)));

        return enParalelo(vendedores, () -> ventaService.registrar(request));
    }

    private static <T> T comoUsuario(UsuarioActual usuario, Callable<T> accion) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsuarioAuthenticationToken(usuario));
        try {
            return accion.call();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private record Resultado(boolean exito, Exception error) {
    }
}
