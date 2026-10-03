package com.ferreteria.config;

import com.ferreteria.entity.Categoria;
import com.ferreteria.entity.Cliente;
import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Proveedor;
import com.ferreteria.entity.SerieCorrelativo;
import com.ferreteria.entity.Stock;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.Usuario;
import com.ferreteria.entity.enums.Rol;
import com.ferreteria.entity.enums.TipoDocumentoIdentidad;
import com.ferreteria.entity.enums.TipoDocumentoVenta;
import com.ferreteria.entity.enums.TipoMovimiento;
import com.ferreteria.entity.enums.TipoUbicacion;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.repository.CategoriaRepository;
import com.ferreteria.repository.ClienteRepository;
import com.ferreteria.repository.EmpresaRepository;
import com.ferreteria.repository.ProductoRepository;
import com.ferreteria.repository.ProveedorRepository;
import com.ferreteria.repository.SerieCorrelativoRepository;
import com.ferreteria.repository.StockRepository;
import com.ferreteria.repository.UbicacionRepository;
import com.ferreteria.repository.UsuarioRepository;
import com.ferreteria.service.stock.LineaMovimiento;
import com.ferreteria.service.stock.MovimientoStockService;
import com.ferreteria.service.stock.OrigenMovimiento;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Datos de demostracion para probar el sistema apenas se levanta. SOLO en el perfil "dev" (nunca en produccion
 * ni en las migraciones). Se cargan una sola vez: si ya existe el "Almacen Central" no hace nada.
 * <p>
 * La contrasena de los usuarios de demostracion viene de la variable DEMO_PASSWORD (no esta en el codigo).
 * El stock inicial se registra con el mismo servicio que usa el sistema, asi queda en el kardex.
 */
@Slf4j
@Component
@Profile("dev")
@Order(2) // despues de crear el admin inicial
@RequiredArgsConstructor
public class DatosDemoConfig implements ApplicationRunner {

    private static final String ALMACEN = "Almacen Central";

    private final EmpresaRepository empresaRepository;
    private final UbicacionRepository ubicacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;
    private final ProveedorRepository proveedorRepository;
    private final ClienteRepository clienteRepository;
    private final SerieCorrelativoRepository serieRepository;
    private final StockRepository stockRepository;
    private final MovimientoStockService movimientoStock;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.demo.password:}")
    private String passwordDemo;

    @Override
    public void run(ApplicationArguments args) {
        if (ubicacionRepository.existsByNombre(ALMACEN)) {
            log.info("Datos de demostracion ya cargados");
            return;
        }
        if (passwordDemo == null || passwordDemo.isBlank()) {
            log.warn("Perfil dev sin DEMO_PASSWORD: no se cargan los datos de demostracion");
            return;
        }
        Usuario admin = usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() == Rol.ADMIN && u.isActivo()).findFirst().orElse(null);
        if (admin == null) {
            log.warn("No hay un ADMIN (configure ADMIN_PASSWORD): no se cargan los datos de demostracion");
            return;
        }
        transactionTemplate.executeWithoutResult(estado -> cargar(admin));
        log.info("Datos de demostracion cargados (usuarios: vendedor1, vendedor2, almacen1)");
    }

    private void cargar(Usuario admin) {
        Empresa constructor = empresa("20601234561", "Ferreteria El Constructor S.A.C.", "El Constructor");
        Empresa laLlave = empresa("20609876542", "Ferreteria La Llave E.I.R.L.", "La Llave");
        Ubicacion almacen = ubicacion(ALMACEN, TipoUbicacion.ALMACEN, null, "Av. Industrial 450, Ate");
        Ubicacion centro = ubicacion("Tienda Centro", TipoUbicacion.TIENDA, constructor, "Jr. Ayacucho 120, Lima");
        Ubicacion norte = ubicacion("Tienda Norte", TipoUbicacion.TIENDA, laLlave, "Av. Tupac Amaru 2300, Comas");
        serie(centro, "NV01");
        serie(norte, "NV02");

        usuario("vendedor1", "Carla Mendoza (Tienda Centro)", Rol.VENDEDOR, centro);
        usuario("vendedor2", "Luis Ramos (Tienda Norte)", Rol.VENDEDOR, norte);
        usuario("almacen1", "Pedro Huaman (Almacen)", Rol.ALMACENERO, almacen);

        proveedor("20100070970", "Aceros Arequipa Distribuidora S.A.", "Rosa Vega", "014567890");
        proveedor("20512345671", "Importadora Ferretera del Peru S.A.C.", "Jorge Salas", "987111222");
        cliente("Juan Perez Quispe", TipoDocumentoIdentidad.DNI, "41234567", "987654321");
        cliente("Constructora Los Andes S.A.C.", TipoDocumentoIdentidad.RUC, "20512345678", "016543210");
        cliente("Maria Lopez (vecina)", TipoDocumentoIdentidad.NINGUNO, null, "912345678");

        Map<String, Categoria> categorias = categoriaRepository.findAll().stream()
                .collect(Collectors.toMap(Categoria::getNombre, Function.identity()));
        List<Producto> productos = List.of(
                producto("CLA-0200", "Clavo de acero 2\"", "Clavos y tornillos", categorias, UnidadBase.UNIDAD,
                        p("Unidad", "1", "0.10"), p("Ciento", "100", "8.00"), p("Millar", "1000", "70.00")),
                producto("TOR-0610", "Tornillo drywall 6 x 1\"", "Clavos y tornillos", categorias, UnidadBase.UNIDAD,
                        p("Unidad", "1", "0.15"), p("Ciento", "100", "12.00")),
                producto("PER-3802", "Perno hexagonal 3/8 x 2\"", "Pernos y tuercas", categorias, UnidadBase.UNIDAD,
                        p("Unidad", "1", "0.80"), p("Caja x 50", "50", "35.00")),
                producto("BIS-0303", "Bisagra capuchina 3\"", "Bisagras y cerrajeria", categorias, UnidadBase.UNIDAD,
                        p("Unidad", "1", "3.50"), p("Par", "2", "6.50")),
                producto("CAN-0040", "Candado de bronce 40 mm", "Bisagras y cerrajeria", categorias,
                        UnidadBase.UNIDAD, p("Unidad", "1", "28.00")),
                producto("MAR-0016", "Martillo de una 16 oz", "Herramientas", categorias, UnidadBase.UNIDAD,
                        p("Unidad", "1", "35.00")),
                producto("FOC-LED9", "Foco LED 9 W luz blanca", "Electricidad", categorias, UnidadBase.UNIDAD,
                        p("Unidad", "1", "6.50"), p("Caja x 10", "10", "60.00")),
                producto("CIN-AIS3", "Cinta aislante 3M negra", "Electricidad", categorias, UnidadBase.UNIDAD,
                        p("Unidad", "1", "4.50")),
                producto("TUB-PVC12", "Tubo PVC 1/2\" x 5 m", "Gasfiteria", categorias, UnidadBase.UNIDAD,
                        p("Unidad", "1", "9.90")),
                producto("PIN-LTXB", "Pintura latex blanco (galon)", "Pinturas", categorias, UnidadBase.UNIDAD,
                        p("Galon", "1", "45.00")),
                producto("CLA-KG03", "Clavo para madera 3\" (a granel)", "Clavos y tornillos", categorias,
                        UnidadBase.KILO, p("Kilo", "1", "7.00"), p("Medio kilo", "0.5", "3.80"),
                        p("Cuarto de kilo", "0.25", "2.00")),
                producto("ALA-N16", "Alambre negro N 16", "Cadenas y cables", categorias, UnidadBase.KILO,
                        p("Kilo", "1", "6.50"), p("Medio kilo", "0.5", "3.50")),
                producto("CAB-THW14", "Cable THW 14 AWG", "Electricidad", categorias, UnidadBase.METRO,
                        p("Metro", "1", "1.30"), p("Rollo 100 m", "100", "115.00")),
                producto("CAD-0516", "Cadena galvanizada 5/16\"", "Cadenas y cables", categorias, UnidadBase.METRO,
                        p("Metro", "1", "9.00")),
                producto("MAN-JAR12", "Manguera de jardin 1/2\"", "Otros", categorias, UnidadBase.METRO,
                        p("Metro", "1", "2.50"), p("Rollo 25 m", "25", "55.00")));

        // Stock inicial (unidad base): almacen, Tienda Centro, Tienda Norte
        int[][] cantidades = {
                {20000, 3000, 2000}, {5000, 800, 600}, {1000, 150, 100}, {300, 40, 30}, {60, 10, 8},
                {40, 8, 6}, {200, 40, 30}, {150, 30, 25}, {120, 20, 15}, {50, 10, 8},
                {300, 50, 40}, {200, 30, 25}, {2000, 300, 200}, {300, 50, 40}, {500, 100, 75}};
        List<Ubicacion> ubicaciones = List.of(almacen, centro, norte);
        for (int u = 0; u < ubicaciones.size(); u++) {
            int columna = u;
            List<LineaMovimiento> lineas = IntStream.range(0, productos.size())
                    .mapToObj(i -> LineaMovimiento.de(productos.get(i), BigDecimal.valueOf(cantidades[i][columna])))
                    .toList();
            movimientoStock.aplicar(ubicaciones.get(u), TipoMovimiento.INVENTARIO_INICIAL, lineas,
                    OrigenMovimiento.ajuste(admin, "Inventario inicial (datos de demostracion)"));
        }
        // Minimos para que el reporte de stock bajo muestre algo (candados y martillos en tiendas)
        stockMinimo(productos.get(4), centro, "12");
        stockMinimo(productos.get(5), norte, "10");
        stockMinimo(productos.get(9), almacen, "60");
    }

    private Empresa empresa(String ruc, String razonSocial, String nombreComercial) {
        Empresa empresa = new Empresa();
        empresa.setRuc(ruc);
        empresa.setRazonSocial(razonSocial);
        empresa.setNombreComercial(nombreComercial);
        return empresaRepository.save(empresa);
    }

    private Ubicacion ubicacion(String nombre, TipoUbicacion tipo, Empresa empresa, String direccion) {
        Ubicacion ubicacion = new Ubicacion();
        ubicacion.setNombre(nombre);
        ubicacion.setTipo(tipo);
        ubicacion.setEmpresa(empresa);
        ubicacion.setDireccion(direccion);
        return ubicacionRepository.save(ubicacion);
    }

    private void serie(Ubicacion tienda, String codigo) {
        SerieCorrelativo serie = new SerieCorrelativo();
        serie.setUbicacion(tienda);
        serie.setTipoDocumento(TipoDocumentoVenta.NOTA_VENTA);
        serie.setSerie(codigo);
        serieRepository.save(serie);
    }

    private void usuario(String username, String nombres, Rol rol, Ubicacion ubicacion) {
        if (usuarioRepository.existsByUsername(username)) {
            return;
        }
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setNombres(nombres);
        usuario.setRol(rol);
        usuario.setUbicacion(ubicacion);
        usuario.setPasswordHash(passwordEncoder.encode(passwordDemo));
        usuarioRepository.save(usuario);
    }

    private void proveedor(String ruc, String razonSocial, String contacto, String telefono) {
        Proveedor proveedor = new Proveedor();
        proveedor.setRuc(ruc);
        proveedor.setRazonSocial(razonSocial);
        proveedor.setContacto(contacto);
        proveedor.setTelefono(telefono);
        proveedorRepository.save(proveedor);
    }

    private void cliente(String nombre, TipoDocumentoIdentidad tipo, String numero, String telefono) {
        Cliente cliente = new Cliente();
        cliente.setNombre(nombre);
        cliente.setTipoDocumento(tipo);
        cliente.setNumeroDocumento(numero);
        cliente.setTelefono(telefono);
        clienteRepository.save(cliente);
    }

    private Producto producto(String codigo, String nombre, String categoria, Map<String, Categoria> categorias,
                              UnidadBase unidad, Presentacion... presentaciones) {
        Producto producto = new Producto();
        producto.setCodigo(codigo);
        producto.setNombre(nombre);
        producto.setCategoria(categorias.get(categoria));
        producto.setUnidadBase(unidad);
        for (int i = 0; i < presentaciones.length; i++) {
            presentaciones[i].setPrincipal(i == 0);
            producto.agregarPresentacion(presentaciones[i]);
        }
        return productoRepository.save(producto);
    }

    private static Presentacion p(String nombre, String factor, String precio) {
        Presentacion presentacion = new Presentacion();
        presentacion.setNombre(nombre);
        presentacion.setFactor(new BigDecimal(factor));
        presentacion.setPrecioVenta(new BigDecimal(precio));
        return presentacion;
    }

    private void stockMinimo(Producto producto, Ubicacion ubicacion, String minimo) {
        Stock stock = stockRepository.findByProductoIdAndUbicacionId(producto.getId(), ubicacion.getId())
                .orElseThrow();
        stock.setStockMinimo(new BigDecimal(minimo));
    }
}
