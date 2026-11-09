package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pedido del dueno: exportar ventas, stock y deudores a Excel, respetando la seguridad por tienda. */
class ExportacionIntegrationTest extends IntegracionTestBase {

    private Producto perno;
    private String vendedor1;

    @BeforeEach
    void preparar() throws Exception {
        perno = datos.producto("PER-1", UnidadBase.UNIDAD, "2.00");
        vendedor1 = datos.token(datos.vendedor1);
        cargarStock(perno, datos.tienda1, 20);
        postCon(vendedor1, "/api/cajas/abrir", Map.of("montoApertura", 0)).andExpect(status().isCreated());
        // Contado de 10 unidades (S/ 20.00) y credito de 5 (S/ 10.00) con adelanto de 4
        postCon(vendedor1, "/api/ventas", Map.of("condicion", "CONTADO",
                "detalles", List.of(Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 10)),
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 20)))).andExpect(status().isCreated());
        Long cliente = leerId(postCon(vendedor1, "/api/clientes", Map.of("nombre", "Juan Perez",
                "tipoDocumento", "DNI", "numeroDocumento", "41234567")).andExpect(status().isCreated()).andReturn(), "$.id");
        postCon(vendedor1, "/api/ventas", Map.of("condicion", "CREDITO", "clienteId", cliente,
                "fechaVencimiento", hoyLima().plusDays(15).toString(),
                "detalles", List.of(Map.of("presentacionId", datos.presentacionId(perno, 0), "cantidad", 5)),
                "pagos", List.of(Map.of("metodoPago", "EFECTIVO", "monto", 4)))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Ventas en Excel: detalle, resumen por tienda y productos, con el total correcto")
    void ventas() throws Exception {
        String hoy = hoyLima().toString();
        byte[] archivo = getCon(vendedor1, "/api/reportes/excel/ventas?desde={d}&hasta={h}", hoy, hoy)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("ventas-" + hoy)))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(3);
            Sheet ventas = libro.getSheet("Ventas");
            assertThat(ventas.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Fecha");
            assertThat(ventas.getLastRowNum()).isEqualTo(5); // titulo, subtitulo, vacia, titulos y 2 ventas
            assertThat(ventas.getRow(5).getCell(5).getStringCellValue()).isEqualTo("Crédito");
            assertThat(ventas.getRow(5).getCell(11).getNumericCellValue()).isEqualTo(6.0); // saldo 10 - 4
            Sheet tiendas = libro.getSheet("Por tienda");
            assertThat(tiendas.getRow(4).getCell(3).getNumericCellValue()).isEqualTo(30.0);
            Sheet productos = libro.getSheet("Productos");
            assertThat(productos.getRow(4).getCell(3).getNumericCellValue()).isEqualTo(15.0);
        }
    }

    @Test
    @DisplayName("Stock y deudores en Excel")
    void stockYDeudores() throws Exception {
        byte[] stock = getCon(vendedor1, "/api/reportes/excel/stock").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(stock))) {
            Sheet hoja = libro.getSheet("Stock");
            assertThat(hoja.getRow(4).getCell(1).getStringCellValue()).contains("PER-1");
            assertThat(hoja.getRow(4).getCell(4).getNumericCellValue()).isEqualTo(5.0); // 20 - 10 - 5
        }
        byte[] deudores = getCon(vendedor1, "/api/reportes/excel/deudores").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(deudores))) {
            Sheet hoja = libro.getSheet("Deudores");
            assertThat(hoja.getRow(4).getCell(0).getStringCellValue()).isEqualTo("Juan Perez");
            assertThat(hoja.getRow(4).getCell(4).getNumericCellValue()).isEqualTo(6.0);
            assertThat(libro.getSheet("Deudas").getRow(4).getCell(6).getNumericCellValue()).isEqualTo(6.0);
        }
    }

    @Test
    @DisplayName("Seguridad: el vendedor de otra tienda no ve estas ventas y el almacenero no exporta ventas")
    void seguridad() throws Exception {
        byte[] archivo = getCon(datos.token(datos.vendedor2), "/api/reportes/excel/ventas").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
            assertThat(libro.getSheet("Ventas").getRow(4).getCell(0).getStringCellValue())
                    .isEqualTo("Sin datos para los filtros elegidos");
        }
        getCon(datos.token(datos.vendedor2), "/api/reportes/excel/ventas?ubicacionId={id}", datos.tienda1.getId())
                .andExpect(status().isForbidden());
        getCon(datos.token(datos.almacenero), "/api/reportes/excel/ventas").andExpect(status().isForbidden());
        getCon(datos.token(datos.almacenero), "/api/reportes/excel/stock").andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/reportes/excel/stock")).andExpect(status().isUnauthorized());
    }
}
