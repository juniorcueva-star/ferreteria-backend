package com.ferreteria.integracion;

import com.ferreteria.entity.Categoria;
import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Proveedor;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.entity.enums.TipoUbicacion;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.repository.CategoriaRepository;
import com.ferreteria.repository.EmpresaRepository;
import com.ferreteria.repository.ProductoRepository;
import com.ferreteria.repository.ProveedorRepository;
import com.ferreteria.repository.StockRepository;
import com.ferreteria.repository.UbicacionRepository;
import com.ferreteria.repository.UsuarioRepository;
import com.ferreteria.security.JwtService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Datos base de las pruebas de integracion: 2 empresas, 1 almacen, 2 tiendas y un usuario por rol
 * (mas un vendedor en cada tienda). Todos con la contrasena {@link #PASSWORD}.
 */
@Component
public class DatosPrueba {

    public static final String PASSWORD = "Clave-Prueba-1";

    private static final String TABLAS = String.join(", ",
            "movimiento_inventario", "pago", "venta_detalle", "venta", "serie_correlativo", "caja_sesion",
            "compra_detalle", "compra", "proveedor", "traslado_detalle", "traslado", "stock", "presentacion",
            "producto", "cliente", "usuario", "ubicacion", "empresa");

    private final JdbcTemplate jdbcTemplate;
    private final EmpresaRepository empresaRepository;
    private final UbicacionRepository ubicacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProveedorRepository proveedorRepository;
    private final StockRepository stockRepository;
    private final String hashPassword;

    public Empresa empresa1;
    public Empresa empresa2;
    public Ubicacion almacen;
    public Ubicacion tienda1;
    public Ubicacion tienda2;
    public Usuario admin;
    public Usuario vendedor1;
    public Usuario vendedor2;
    public Usuario almacenero;

    public DatosPrueba(JdbcTemplate jdbcTemplate, EmpresaRepository empresaRepository,
                       UbicacionRepository ubicacionRepository, UsuarioRepository usuarioRepository,
                       JwtService jwtService, PasswordEncoder passwordEncoder,
                       ProductoRepository productoRepository, CategoriaRepository categoriaRepository,
                       ProveedorRepository proveedorRepository, StockRepository stockRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.proveedorRepository = proveedorRepository;
        this.stockRepository = stockRepository;
        this.empresaRepository = empresaRepository;
        this.ubicacionRepository = ubicacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.jwtService = jwtService;
        this.hashPassword = passwordEncoder.encode(PASSWORD); // una sola vez: BCrypt es lento a proposito
    }

    /** Borra todo lo que crean las pruebas (no toca los catalogos de V2) y vuelve a crear los datos base. */
    public void reiniciar() {
        jdbcTemplate.execute("TRUNCATE TABLE " + TABLAS + " RESTART IDENTITY CASCADE");
        empresa1 = empresa("20111111111", "Ferreteria Uno S.A.C.");
        empresa2 = empresa("20222222222", "Ferreteria Dos S.A.C.");
        almacen = ubicacion("Almacen Central", TipoUbicacion.ALMACEN, null);
        tienda1 = ubicacion("Tienda 1", TipoUbicacion.TIENDA, empresa1);
        tienda2 = ubicacion("Tienda 2", TipoUbicacion.TIENDA, empresa2);
        admin = usuario("admin", Rol.ADMIN, null);
        vendedor1 = usuario("vendedor1", Rol.VENDEDOR, tienda1);
        vendedor2 = usuario("vendedor2", Rol.VENDEDOR, tienda2);
        almacenero = usuario("almacenero", Rol.ALMACENERO, almacen);
    }

    /**
     * Producto con una presentacion base (factor 1, principal) y opcionalmente una segunda presentacion.
     * Ej: producto("CEM", UnidadBase.UNIDAD, "28.50", "Paquete x 10", "10", "270.00").
     */
    public Producto producto(String codigo, UnidadBase unidad, String precioBase, String... otra) {
        Categoria categoria = categoriaRepository.findAll().getFirst();
        Producto producto = new Producto();
        producto.setCodigo(codigo);
        producto.setNombre("Producto " + codigo);
        producto.setCategoria(categoria);
        producto.setUnidadBase(unidad);
        producto.agregarPresentacion(presentacion(unidad.name(), "1", precioBase, true));
        if (otra.length == 3) {
            producto.agregarPresentacion(presentacion(otra[0], otra[1], otra[2], false));
        }
        return productoRepository.save(producto);
    }

    public Proveedor proveedor(String ruc) {
        Proveedor proveedor = new Proveedor();
        proveedor.setRuc(ruc);
        proveedor.setRazonSocial("Proveedor " + ruc);
        return proveedorRepository.save(proveedor);
    }

    /** Stock actual (0 si el producto nunca estuvo en esa ubicacion). */
    public BigDecimal stock(Producto producto, Ubicacion ubicacion) {
        return stockRepository.findByProductoIdAndUbicacionId(producto.getId(), ubicacion.getId())
                .map(s -> s.getCantidad()).orElse(BigDecimal.ZERO);
    }

    public Long presentacionId(Producto producto, int indice) {
        return producto.getPresentaciones().get(indice).getId();
    }

    private Presentacion presentacion(String nombre, String factor, String precio, boolean principal) {
        Presentacion presentacion = new Presentacion();
        presentacion.setNombre(nombre);
        presentacion.setFactor(new BigDecimal(factor));
        presentacion.setPrecioVenta(new BigDecimal(precio));
        presentacion.setPrincipal(principal);
        return presentacion;
    }

    public String token(Usuario usuario) {
        return jwtService.generar(usuario).token();
    }

    public Usuario usuario(String username, Rol rol, Ubicacion ubicacion) {
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setNombres("Usuario " + username);
        usuario.setRol(rol);
        usuario.setUbicacion(ubicacion);
        usuario.setPasswordHash(hashPassword);
        return usuarioRepository.save(usuario);
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
}
