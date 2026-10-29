package com.ferreteria.integracion;

import com.ferreteria.dto.caja.AperturaCajaRequest;
import com.ferreteria.dto.cotizaciones.CotizacionDetalleRequest;
import com.ferreteria.dto.cotizaciones.CotizacionRequest;
import com.ferreteria.dto.inventario.InventarioInicialRequest;
import com.ferreteria.dto.inventario.LineaProductoRequest;
import com.ferreteria.dto.ventas.PagoRequest;
import com.ferreteria.dto.ventas.VentaDesdeCotizacionRequest;
import com.ferreteria.entity.*;
import com.ferreteria.entity.enums.*;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.*;
import com.ferreteria.security.UsuarioActual;
import com.ferreteria.security.UsuarioAuthenticationToken;
import com.ferreteria.service.CajaService;
import com.ferreteria.service.CotizacionService;
import com.ferreteria.service.InventarioService;
import com.ferreteria.service.VentaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.core.context.SecurityContextHolder.clearContext;
import static org.springframework.security.core.context.SecurityContextHolder.getContext;

/** Fixtures con sufijo unico: nunca limpia ni reutiliza tablas compartidas de integration. */
@SpringBootTest
@ActiveProfiles("integration")
class VentaDesdeCotizacionIntegrationTest {
    @Autowired private javax.sql.DataSource dataSource;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private UbicacionRepository ubicacionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private CotizacionService cotizacionService;
    @Autowired private InventarioService inventarioService;
    @Autowired private CajaService cajaService;
    @Autowired private VentaService ventaService;

    @Test
    void convierteSnapshotMillarUnaSolaVezYDescuentaQuinientasUnidades() throws Exception {
        Fixture f = fixture("ok", new BigDecimal("600"));
        Long cotizacionId = como(f.admin, () -> cotizacionEmitida(f));
        BigDecimal antes = stock(f);

        var venta = como(f.admin, () -> ventaService.registrarDesdeCotizacion(cotizacionId,
                new VentaDesdeCotizacionRequest(CondicionVenta.CONTADO, null,
                        List.of(new PagoRequest("EFECTIVO", new BigDecimal("350.00"), null)))));

        assertThat(venta.total()).isEqualByComparingTo("350.00");
        var cotizacion = como(f.admin, () -> cotizacionService.obtener(cotizacionId));
        assertThat(cotizacion.venta().id()).isEqualTo(venta.id());
        assertThat(cotizacion.venta().numeroDocumento()).isEqualTo(venta.numeroDocumento());
        var detalleVenta = como(f.admin, () -> ventaService.obtener(venta.id()));
        assertThat(detalleVenta.cotizacionId()).isEqualTo(cotizacionId);
        assertThat(detalleVenta.cotizacionCodigo()).isEqualTo(cotizacion.codigo());
        var listado = como(f.admin, () -> cotizacionService.listar(f.tienda.getId(), null, org.springframework.data.domain.PageRequest.of(0, 20)));
        assertThat(listado.contenido()).anySatisfy(c -> {
            assertThat(c.id()).isEqualTo(cotizacionId);
            assertThat(c.venta().id()).isEqualTo(venta.id());
        });
        assertThat(ventaRepository.countByCotizacionId(cotizacionId)).isEqualTo(1);
        assertThat(venta.detalles()).singleElement().satisfies(d -> {
            assertThat(d.cantidad()).isEqualByComparingTo("0.500");
            assertThat(d.factor()).isEqualByComparingTo("1000.000");
            assertThat(d.precioUnitario()).isEqualByComparingTo("700.00");
            assertThat(d.cantidadBase()).isEqualByComparingTo("500.000");
        });
        assertThat(stock(f)).isEqualByComparingTo(antes.subtract(new BigDecimal("500")));
        assertThat(movimientosVenta(venta.id())).isEqualTo(1);

        assertThatThrownBy(() -> como(f.admin, () -> ventaService.registrarDesdeCotizacion(cotizacionId,
                new VentaDesdeCotizacionRequest(CondicionVenta.CONTADO, null,
                        List.of(new PagoRequest("EFECTIVO", new BigDecimal("350.00"), null))))))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(ventaRepository.countByCotizacionId(cotizacionId)).isEqualTo(1);
        assertThat(stock(f)).isEqualByComparingTo(antes.subtract(new BigDecimal("500")));
    }

    @Test
    void stockInsuficienteNoCreaVentaNiKardex() throws Exception {
        Fixture f = fixture("sin-stock", new BigDecimal("499"));
        Long cotizacionId = como(f.admin, () -> cotizacionEmitida(f));
        BigDecimal antes = stock(f);
        long movimientosAntes = movimientosVenta(null);

        assertThatThrownBy(() -> como(f.admin, () -> ventaService.registrarDesdeCotizacion(cotizacionId,
                new VentaDesdeCotizacionRequest(CondicionVenta.CONTADO, null,
                        List.of(new PagoRequest("EFECTIVO", new BigDecimal("350.00"), null))))))
                .isInstanceOf(RuntimeException.class);
        assertThat(ventaRepository.countByCotizacionId(cotizacionId)).isZero();
        assertThat(stock(f)).isEqualByComparingTo(antes);
        assertThat(movimientosVenta(null)).isEqualTo(movimientosAntes);
    }

    @Test
    void vendedorDeLaUbicacionPuedeConvertir() throws Exception {
        Fixture f = fixture("vendedor", new BigDecimal("600"));
        Usuario vendedor = usuario("vendedor", Rol.VENDEDOR, f.tienda);
        Long cotizacionId = como(vendedor, () -> cotizacionEmitida(f));
        como(vendedor, () -> { cajaService.abrir(new AperturaCajaRequest(null, BigDecimal.ZERO)); return null; });
        var venta = como(vendedor, () -> ventaService.registrarDesdeCotizacion(cotizacionId, solicitud()));
        assertThat(ventaRepository.countByCotizacionId(cotizacionId)).isEqualTo(1);
        assertThat(venta.total()).isEqualByComparingTo("350.00");
    }

    @Test
    void almaceneroYOtraUbicacionNoConviertenSinEfectos() throws Exception {
        Fixture f = fixture("acceso", new BigDecimal("600"));
        Long cotizacionId = como(f.admin, () -> cotizacionEmitida(f));
        Usuario almacen = usuario("almacen", Rol.ALMACENERO, f.tienda);
        Empresa empresa = f.tienda.getEmpresa();
        Ubicacion otra = new Ubicacion(); otra.setNombre("Otra " + UUID.randomUUID()); otra.setTipo(TipoUbicacion.TIENDA); otra.setEmpresa(empresa); otra = ubicacionRepository.save(otra);
        Usuario otro = usuario("otro", Rol.VENDEDOR, otra);
        BigDecimal antes = stock(f); long kardex = movimientosVenta(null);
        assertThatThrownBy(() -> como(almacen, () -> ventaService.registrarDesdeCotizacion(cotizacionId, solicitud()))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> como(otro, () -> ventaService.registrarDesdeCotizacion(cotizacionId, solicitud()))).isInstanceOf(RuntimeException.class);
        assertThat(ventaRepository.countByCotizacionId(cotizacionId)).isZero(); assertThat(stock(f)).isEqualByComparingTo(antes); assertThat(movimientosVenta(null)).isEqualTo(kardex);
    }

    @Test
    void borradorYAnuladaNoConviertenSinEfectos() throws Exception {
        Fixture f = fixture("estados", new BigDecimal("600"));
        Long borrador = como(f.admin, () -> cotizacionService.crear(new CotizacionRequest(f.tienda.getId(), f.cliente.getId(), null, List.of(new CotizacionDetalleRequest(f.presentacion.getId(), new BigDecimal("0.5"), null, null)))).id());
        Long anulada = como(f.admin, () -> { Long id = cotizacionEmitida(f); cotizacionService.anular(id); return id; });
        BigDecimal antes = stock(f); long kardex = movimientosVenta(null);
        assertThatThrownBy(() -> como(f.admin, () -> ventaService.registrarDesdeCotizacion(borrador, solicitud()))).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> como(f.admin, () -> ventaService.registrarDesdeCotizacion(anulada, solicitud()))).isInstanceOf(ReglaNegocioException.class);
        assertThat(ventaRepository.countByCotizacionId(borrador)).isZero(); assertThat(ventaRepository.countByCotizacionId(anulada)).isZero(); assertThat(stock(f)).isEqualByComparingTo(antes); assertThat(movimientosVenta(null)).isEqualTo(kardex);
    }

    @Test
    void vendedorConsultaDisponibilidadSinAccesoOperativoAOtraTienda() throws Exception {
        Fixture f = fixture("disponibilidad", new BigDecimal("500"));
        Ubicacion otra = new Ubicacion(); otra.setNombre("Otra tienda IT " + UUID.randomUUID());
        otra.setTipo(TipoUbicacion.TIENDA); otra.setEmpresa(f.tienda.getEmpresa());
        otra = ubicacionRepository.save(otra);
        Long otraId = otra.getId();
        como(f.admin, () -> inventarioService.registrarInventarioInicial(new InventarioInicialRequest(otraId,
                List.of(new LineaProductoRequest(f.producto.getId(), null, new BigDecimal("200"))))));
        Usuario vendedor = usuario("vendedor", Rol.VENDEDOR, f.tienda);
        var disponible = como(vendedor, () -> inventarioService.disponibilidadParaVenta(List.of(f.producto.getId())));
        assertThat(disponible).anySatisfy(d -> { assertThat(d.ubicacionId()).isEqualTo(otraId); assertThat(d.cantidad()).isEqualByComparingTo("200"); });
        assertThatThrownBy(() -> como(vendedor, () -> inventarioService.listarStock(otraId, null, null, null, false,
                org.springframework.data.domain.PageRequest.of(0, 20))))
                .isInstanceOf(com.ferreteria.exception.AccesoDenegadoException.class);
    }

    private Fixture fixture(String caso, BigDecimal stock) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThat(c.getMetaData().getURL()).isEqualTo("jdbc:postgresql://127.0.0.1:5546/ivera_cotizacion_it");
        }
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Empresa empresa = new Empresa(); empresa.setRuc("209" + sufijo.replaceAll("[a-f]", "1").substring(0, 8)); empresa.setRazonSocial("IT " + sufijo); empresa = empresaRepository.save(empresa);
        Ubicacion tienda = new Ubicacion(); tienda.setNombre("IT venta " + caso + " " + sufijo); tienda.setTipo(TipoUbicacion.TIENDA); tienda.setEmpresa(empresa); tienda = ubicacionRepository.save(tienda);
        Usuario admin = new Usuario(); admin.setNombres("Admin IT"); admin.setUsername("admin_" + sufijo); admin.setPasswordHash("$2a$10$abcdefghijklmnopqrstuuabcdefghijklmno12345678901234567890"); admin.setRol(Rol.ADMIN); admin = usuarioRepository.save(admin);
        Categoria categoria = new Categoria(); categoria.setNombre("Categoria IT " + sufijo); categoria = categoriaRepository.save(categoria);
        Producto producto = new Producto(); producto.setCodigo("IT" + sufijo); producto.setNombre("Producto MILLAR " + sufijo); producto.setCategoria(categoria); producto.setUnidadBase(UnidadBase.UNIDAD);
        Presentacion millar = new Presentacion(); millar.setNombre("MILLAR"); millar.setFactor(new BigDecimal("1000")); millar.setPrecioVenta(new BigDecimal("700")); millar.setPrincipal(true); producto.agregarPresentacion(millar); producto = productoRepository.saveAndFlush(producto);
        Cliente cliente = new Cliente(); cliente.setNombre("Cliente IT " + sufijo); cliente.setTipoDocumento(TipoDocumentoIdentidad.DNI); cliente.setNumeroDocumento("7" + sufijo.replaceAll("[a-f]", "1").substring(0, 7)); cliente = clienteRepository.save(cliente);
        Fixture f = new Fixture(admin, tienda, cliente, producto, producto.getPresentaciones().getFirst());
        como(f.admin, () -> { inventarioService.registrarInventarioInicial(new InventarioInicialRequest(f.tienda.getId(), List.of(new LineaProductoRequest(f.producto.getId(), null, stock)))); cajaService.abrir(new AperturaCajaRequest(f.tienda.getId(), BigDecimal.ZERO)); return null; });
        return f;
    }

    private Long cotizacionEmitida(Fixture f) {
        var creada = cotizacionService.crear(new CotizacionRequest(f.tienda.getId(), f.cliente.getId(), null,
                List.of(new CotizacionDetalleRequest(f.presentacion.getId(), new BigDecimal("0.5"), null, null))));
        cotizacionService.emitir(creada.id());
        return creada.id();
    }

    private VentaDesdeCotizacionRequest solicitud() { return new VentaDesdeCotizacionRequest(CondicionVenta.CONTADO, null, List.of(new PagoRequest("EFECTIVO", new BigDecimal("350.00"), null))); }
    private Usuario usuario(String prefijo, Rol rol, Ubicacion ubicacion) { Usuario usuario = new Usuario(); usuario.setNombres(prefijo); usuario.setUsername(prefijo + "_" + UUID.randomUUID()); usuario.setPasswordHash("$2a$10$abcdefghijklmnopqrstuuabcdefghijklmno12345678901234567890"); usuario.setRol(rol); usuario.setUbicacion(ubicacion); return usuarioRepository.save(usuario); }

    private BigDecimal stock(Fixture f) { return data("select cantidad from stock where producto_id = ? and ubicacion_id = ?", f.producto.getId(), f.tienda.getId()); }
    private long movimientosVenta(Long ventaId) { return data("select count(*) from movimiento_inventario where tipo = 'VENTA'" + (ventaId == null ? "" : " and venta_id = " + ventaId)); }
    @SuppressWarnings("unchecked") private <T> T data(String sql, Object... args) { return (T) new org.springframework.jdbc.core.JdbcTemplate(dataSource).queryForObject(sql, args, Object.class); }
    private static <T> T como(Usuario u, Callable<T> accion) throws Exception { getContext().setAuthentication(new UsuarioAuthenticationToken(new UsuarioActual(u.getId(), u.getUsername(), u.getNombres(), u.getRol(), u.getUbicacion() == null ? null : u.getUbicacion().getId()))); try { return accion.call(); } finally { clearContext(); } }
    private record Fixture(Usuario admin, Ubicacion tienda, Cliente cliente, Producto producto, Presentacion presentacion) {}
}
