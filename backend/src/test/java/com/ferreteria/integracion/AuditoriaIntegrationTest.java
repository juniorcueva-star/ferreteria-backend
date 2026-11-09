package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import com.ferreteria.repository.AuditoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pedido del dueno: saber quien cambio precios, stock o productos, y cuando. */
class AuditoriaIntegrationTest extends IntegracionTestBase {

    @Autowired private AuditoriaRepository auditoriaRepository;

    private Producto perno;
    private String admin;

    @BeforeEach
    void preparar() {
        jdbcLimpiar();
        perno = datos.producto("PER-1", UnidadBase.UNIDAD, "0.50");
        admin = datos.token(datos.admin);
    }

    private void jdbcLimpiar() {
        auditoriaRepository.deleteAll();
    }

    private Map<String, Object> producto(String nombre) {
        Map<String, Object> datosProducto = new HashMap<>();
        datosProducto.put("codigo", "PER-1");
        datosProducto.put("nombre", nombre);
        datosProducto.put("categoriaId", perno.getCategoria().getId());
        datosProducto.put("activo", true);
        // Los demas campos se envian igual que estan, para que solo cambie lo que la prueba indica
        datosProducto.put("descripcion", perno.getDescripcion());
        datosProducto.put("marca", perno.getMarca());
        datosProducto.put("tipo", perno.getTipo());
        datosProducto.put("acabado", perno.getAcabado());
        datosProducto.put("medida", perno.getMedida());
        return datosProducto;
    }

    @Test
    @DisplayName("Registra el cambio de precio y la edicion del producto con el usuario y lo que cambio")
    void precioYProducto() throws Exception {
        Long presentacion = datos.presentacionId(perno, 0);
        putCon(admin, "/api/productos/{id}/presentaciones/{p}", Map.of("nombre", "Unidad", "factor", 1,
                "precioVenta", 0.60), perno.getId(), presentacion).andExpect(status().isOk());
        putCon(admin, "/api/productos/{id}", producto("Perno hexagonal"), perno.getId()).andExpect(status().isOk());

        getCon(admin, "/api/auditoria?entidad=PRECIO")
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].usuario").value("admin"))
                .andExpect(jsonPath("$.contenido[0].entidadId").value(perno.getId()))
                .andExpect(jsonPath("$.contenido[0].descripcion").value(containsString("S/ 0.50 → S/ 0.60")));
        getCon(admin, "/api/auditoria?entidad=PRODUCTO&entidadId={id}", perno.getId())
                .andExpect(jsonPath("$.contenido[0].accion").value("EDITAR"))
                .andExpect(jsonPath("$.contenido[0].descripcion").value(containsString("Nombre: ")))
                .andExpect(jsonPath("$.contenido[0].descripcion").value(containsString("→ Perno hexagonal")));
    }

    @Test
    @DisplayName("Guardar sin cambios no registra nada y un cambio que falla tampoco deja registro")
    void sinCambiosNiErrores() throws Exception {
        Producto otro = datos.producto("TOR-1", UnidadBase.UNIDAD, "0.20");
        Map<String, Object> igual = producto(perno.getNombre());
        igual.put("marca", "VARIADO"); // la marca vacia se guarda como VARIADO
        putCon(admin, "/api/productos/{id}", igual, perno.getId()).andExpect(status().isOk());
        auditoriaRepository.deleteAll();
        putCon(admin, "/api/productos/{id}", igual, perno.getId()).andExpect(status().isOk());
        Map<String, Object> duplicado = producto("Otro nombre");
        duplicado.put("codigo", otro.getCodigo());
        putCon(admin, "/api/productos/{id}", duplicado, perno.getId()).andExpect(status().isConflict());
        assertThat(auditoriaRepository.findAll()).extracting(a -> a.getDescripcion()).isEmpty();
    }

    @Test
    @DisplayName("Registra el stock minimo, los ajustes y la venta anulada; solo el ADMIN consulta")
    void stockYVentas() throws Exception {
        cargarStock(perno, datos.tienda1, 20);
        putCon(admin, "/api/inventario/stock/minimo", Map.of("productoId", perno.getId(),
                "ubicacionId", datos.tienda1.getId(), "stockMinimo", 5)).andExpect(status().isOk());
        postCon(admin, "/api/inventario/ajustes", Map.of("ubicacionId", datos.tienda1.getId(), "tipo", "SALIDA",
                "motivo", "Se rompio una caja", "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", 2))))
                .andExpect(status().isCreated());
        String vendedor = datos.token(datos.vendedor1);
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        Long venta = leerId(postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO",
                        "detalles", List.of(Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 2)),
                        "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 1))))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        postCon(vendedor, "/api/ventas/{id}/anular", Map.of("motivo", "El cliente se arrepintio"), venta)
                .andExpect(status().isOk());

        getCon(admin, "/api/auditoria?entidad=STOCK")
                .andExpect(jsonPath("$.contenido[?(@.accion == 'MINIMO')].descripcion")
                        .value(hasItem(containsString("en Tienda 1: 0 → 5"))))
                .andExpect(jsonPath("$.contenido[?(@.accion == 'AJUSTE')].descripcion")
                        .value(hasItem(containsString("Ajuste de salida en Tienda 1"))))
                .andExpect(jsonPath("$.contenido[?(@.accion == 'INICIAL')].descripcion")
                        .value(hasItem(containsString("Inventario inicial"))));
        getCon(admin, "/api/auditoria?texto=rompio")
                .andExpect(jsonPath("$.contenido.length()").value(1));
        getCon(admin, "/api/auditoria?entidad=VENTA")
                .andExpect(jsonPath("$.contenido[0].usuario").value("vendedor1"))
                .andExpect(jsonPath("$.contenido[0].descripcion").value(containsString("El cliente se arrepintio")));
        getCon(vendedor, "/api/auditoria").andExpect(status().isForbidden());
    }
}
