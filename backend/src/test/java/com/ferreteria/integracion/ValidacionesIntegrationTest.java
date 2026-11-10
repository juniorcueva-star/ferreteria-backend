package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pedido del dueno: no se aceptan cantidades negativas, letras donde van numeros, telefonos que no sean celulares de
 * 9 digitos que empiezan con 9, ni numeros en los nombres de personas.
 */
class ValidacionesIntegrationTest extends IntegracionTestBase {

    private Producto perno;
    private String admin;
    private String vendedor;

    @BeforeEach
    void preparar() throws Exception {
        perno = datos.producto("PER-1", UnidadBase.UNIDAD, "0.50");
        cargarStock(perno, datos.tienda1, 50);
        admin = datos.token(datos.admin);
        vendedor = datos.token(datos.vendedor1);
    }

    private static Map<String, Object> mapa(Object... pares) {
        Map<String, Object> mapa = new HashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put((String) pares[i], pares[i + 1]);
        }
        return mapa;
    }

    @Test
    @DisplayName("Cantidades y montos negativos o con letras se rechazan")
    void numeros() throws Exception {
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", -10)).andExpect(status().isBadRequest());
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", "diez")).andExpect(status().isBadRequest());
        postCon(vendedor, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        Long presentacion = datos.presentacionId(perno, 0);
        for (Object cantidad : List.of(-1, 0, "abc")) {
            postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO",
                    "detalles", List.of(Map.of("presentacionId", presentacion, "cantidad", cantidad)),
                    "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 1)))).andExpect(status().isBadRequest());
        }
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO",
                "detalles", List.of(Map.of("presentacionId", presentacion, "cantidad", 1, "descuento", -0.10)),
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 1)))).andExpect(status().isBadRequest());
        postCon(vendedor, "/api/ventas", Map.of("condicion", "CONTADO",
                "detalles", List.of(Map.of("presentacionId", presentacion, "cantidad", 1)),
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", -1)))).andExpect(status().isBadRequest());
        putCon(admin, "/api/productos/{id}/presentaciones/{p}", Map.of("nombre", "Unidad", "factor", 1, "precioVenta", -5),
                perno.getId(), presentacion).andExpect(status().isBadRequest());
        postCon(admin, "/api/inventario/ajustes", Map.of("ubicacionId", datos.tienda1.getId(), "tipo", "SALIDA",
                "motivo", "Prueba de negativos", "detalles", List.of(Map.of("productoId", perno.getId(), "cantidad", -3))))
                .andExpect(status().isBadRequest());
        putCon(admin, "/api/inventario/stock/minimo", Map.of("productoId", perno.getId(),
                "ubicacionId", datos.tienda1.getId(), "stockMinimo", -1)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Telefonos: celular de 9 digitos que empieza con 9; se aceptan espacios al escribirlo")
    void telefonos() throws Exception {
        for (String malo : List.of("12345", "014567890", "98765432a", "8765432100")) {
            postCon(vendedor, "/api/clientes", Map.of("nombre", "Juan Perez", "telefono", malo))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.mensaje").value(containsString("9 digitos")));
        }
        postCon(vendedor, "/api/clientes", Map.of("nombre", "Juan Perez", "telefono", "987 654 321"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.telefono").value("987654321"));
        postCon(admin, "/api/proveedores", Map.of("razonSocial", "Aceros SAC", "telefono", "014567890"))
                .andExpect(status().isUnprocessableContent());
        putCon(admin, "/api/empresas/{id}", Map.of("ruc", "20111111111", "razonSocial", "Ferreteria Uno S.A.C.",
                "telefono", "950104503/947 319 013"), datos.empresa1.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value("950 104 503 / 947 319 013"));
    }

    @Test
    @DisplayName("Nombres de personas solo con letras; razones sociales y productos con al menos una letra; RUC valido")
    void nombresYDocumentos() throws Exception {
        postCon(vendedor, "/api/clientes", Map.of("nombre", "Juan123")).andExpect(status().isUnprocessableContent());
        postCon(vendedor, "/api/clientes", Map.of("nombre", "José Ñañez-Quispe")).andExpect(status().isCreated());
        postCon(vendedor, "/api/clientes", Map.of("nombre", "Constructora 2000 S.A.C.", "tipoDocumento", "RUC",
                "numeroDocumento", "20512345678")).andExpect(status().isCreated());
        postCon(vendedor, "/api/clientes", Map.of("nombre", "Empresa Rara SAC", "tipoDocumento", "RUC",
                "numeroDocumento", "30512345678")).andExpect(status().isUnprocessableContent());
        postCon(vendedor, "/api/clientes", Map.of("nombre", "Pedro Diaz", "tipoDocumento", "DNI",
                "numeroDocumento", "1234567A")).andExpect(status().isUnprocessableContent());
        postCon(admin, "/api/proveedores", mapa("razonSocial", "Aceros SAC", "contacto", "Rosa 2"))
                .andExpect(status().isUnprocessableContent());
        postCon(admin, "/api/proveedores", mapa("razonSocial", "12345", "contacto", "Rosa Vega"))
                .andExpect(status().isUnprocessableContent());
        postCon(admin, "/api/usuarios", Map.of("username", "ana2", "nombres", "Ana 2", "rol", "VENDEDOR",
                "ubicacionId", datos.tienda1.getId(), "password", "Clave12345")).andExpect(status().isUnprocessableContent());
        postCon(admin, "/api/categorias", Map.of("nombre", "123")).andExpect(status().isUnprocessableContent());
    }
}
