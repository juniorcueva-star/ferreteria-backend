package com.ferreteria.integracion;

import com.ferreteria.entity.Categoria;
import com.ferreteria.entity.Cliente;
import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.entity.enums.TipoDocumentoIdentidad;
import com.ferreteria.entity.enums.TipoUbicacion;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.repository.CategoriaRepository;
import com.ferreteria.repository.ClienteRepository;
import com.ferreteria.repository.EmpresaRepository;
import com.ferreteria.repository.ProductoRepository;
import com.ferreteria.repository.UbicacionRepository;
import com.ferreteria.repository.UsuarioRepository;
import com.ferreteria.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Base exclusiva de cotizaciones; no comparte el limpiado ni los datos de otras fases. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
abstract class IntegracionCotizacionTestBase {
    private static final String TABLAS = String.join(", ",
            "cotizacion_detalle", "cotizacion", "movimiento_inventario", "pago", "venta_detalle", "venta",
            "serie_correlativo", "caja_sesion", "compra_detalle", "compra", "proveedor", "traslado_detalle",
            "traslado", "stock", "presentacion", "producto", "cliente", "usuario", "ubicacion", "empresa");

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ClienteRepository clienteRepository;
    @Autowired protected ProductoRepository productoRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private UbicacionRepository ubicacionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Value("${spring.datasource.url}") private String datasourceUrl;
    @Value("${app.integracion.usuario-esperado}") private String usuarioEsperado;

    protected Ubicacion tienda1;
    protected Ubicacion tienda2;
    protected Usuario admin;
    protected Usuario vendedor1;
    protected Usuario vendedor2;
    protected Usuario almacenero;

    @BeforeEach
    void reiniciarDatosCotizacion() {
        verificarDestinoAntesDeTruncar();
        jdbc.execute("TRUNCATE TABLE " + TABLAS + " RESTART IDENTITY CASCADE");

        Empresa empresa1 = empresa("20111111111", "Ferreteria Uno S.A.C.");
        Empresa empresa2 = empresa("20222222222", "Ferreteria Dos S.A.C.");
        Ubicacion almacen = ubicacion("Almacen Central", TipoUbicacion.ALMACEN, null);
        tienda1 = ubicacion("Tienda 1", TipoUbicacion.TIENDA, empresa1);
        tienda2 = ubicacion("Tienda 2", TipoUbicacion.TIENDA, empresa2);
        admin = usuario("admin-cot", Rol.ADMIN, null);
        vendedor1 = usuario("vendedor-cot-1", Rol.VENDEDOR, tienda1);
        vendedor2 = usuario("vendedor-cot-2", Rol.VENDEDOR, tienda2);
        almacenero = usuario("almacenero-cot", Rol.ALMACENERO, almacen);
    }

    protected Cliente cliente(String nombre) {
        Cliente cliente = new Cliente();
        cliente.setNombre(nombre);
        cliente.setTipoDocumento(TipoDocumentoIdentidad.DNI);
        cliente.setNumeroDocumento("7000000" + clienteRepository.count());
        return clienteRepository.save(cliente);
    }

    protected Producto producto(String codigo, String precioBase, String nombrePresentacion, String factor, String precioPresentacion) {
        Categoria categoria = categoriaRepository.findAll().getFirst();
        Producto producto = new Producto();
        producto.setCodigo(codigo);
        producto.setNombre("Producto " + codigo);
        producto.setCategoria(categoria);
        producto.setUnidadBase(UnidadBase.UNIDAD);
        producto.agregarPresentacion(presentacion("UNIDAD", "1", precioBase, true));
        producto.agregarPresentacion(presentacion(nombrePresentacion, factor, precioPresentacion, false));
        return productoRepository.saveAndFlush(producto);
    }

    protected long estadoComercial() {
        return jdbc.queryForObject("select count(*) from stock", Long.class)
                + jdbc.queryForObject("select count(*) from movimiento_inventario", Long.class)
                + jdbc.queryForObject("select count(*) from venta", Long.class)
                + jdbc.queryForObject("select count(*) from pago", Long.class)
                + jdbc.queryForObject("select count(*) from caja_sesion", Long.class);
    }

    protected String token(Usuario usuario) {
        return jwtService.generar(usuario).token();
    }

    protected ResultActions getCon(String token, String url, Object... variables) throws Exception {
        return mockMvc.perform(get(url, variables).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    protected ResultActions postCon(String token, String url, Object cuerpo, Object... variables) throws Exception {
        return mockMvc.perform(post(url, variables).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)));
    }

    protected ResultActions putCon(String token, String url, Object cuerpo, Object... variables) throws Exception {
        return mockMvc.perform(put(url, variables).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)));
    }

    protected static Long id(MvcResult resultado) throws Exception {
        return ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void verificarDestinoAntesDeTruncar() {
        URI uri = URI.create(datasourceUrl.substring("jdbc:".length()));
        Map<String, Object> identidad = jdbc.queryForMap("select current_database() as base, current_user as usuario");
        assertThat(uri.getPort()).isEqualTo(5546);
        assertThat(uri.getPath()).isEqualTo("/ivera_cotizacion_it");
        assertThat(identidad.get("base")).isEqualTo("ivera_cotizacion_it");
        assertThat(identidad.get("usuario")).isEqualTo(usuarioEsperado);
    }

    private Presentacion presentacion(String nombre, String factor, String precio, boolean principal) {
        Presentacion presentacion = new Presentacion();
        presentacion.setNombre(nombre);
        presentacion.setFactor(new BigDecimal(factor));
        presentacion.setPrecioVenta(new BigDecimal(precio));
        presentacion.setPrincipal(principal);
        return presentacion;
    }

    private Empresa empresa(String ruc, String razonSocial) {
        Empresa empresa = new Empresa();
        empresa.setRuc(ruc);
        empresa.setRazonSocial(razonSocial);
        return empresaRepository.save(empresa);
    }

    private Ubicacion ubicacion(String nombre, TipoUbicacion tipo, Empresa empresa) {
        Ubicacion ubicacion = new Ubicacion();
        ubicacion.setNombre(nombre);
        ubicacion.setTipo(tipo);
        ubicacion.setEmpresa(empresa);
        return ubicacionRepository.save(ubicacion);
    }

    private Usuario usuario(String username, Rol rol, Ubicacion ubicacion) {
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setNombres("Usuario " + username);
        usuario.setRol(rol);
        usuario.setUbicacion(ubicacion);
        usuario.setPasswordHash(passwordEncoder.encode("Clave-Cotizacion-1"));
        return usuarioRepository.save(usuario);
    }
}
