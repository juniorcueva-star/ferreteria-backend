package com.ferreteria.integracion;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lista de precios para el cliente y etiquetas con codigo de barras para los estantes. */
class CatalogoDocumentosIntegrationTest extends IntegracionTestBase {

    private Producto perno;
    private Producto lija;
    private String admin;
    private String vendedor;

    @BeforeEach
    void preparar() throws Exception {
        perno = datos.producto("PER-1", UnidadBase.UNIDAD, "0.50", "Ciento", "100", "40.00");
        lija = datos.producto("LIJ-1", UnidadBase.UNIDAD, "2.00");
        admin = datos.token(datos.admin);
        vendedor = datos.token(datos.vendedor1);
        LocalDate hoy = hoyLima();
        putCon(admin, "/api/productos/" + lija.getId() + "/presentaciones/" + datos.presentacionId(lija, 0) + "/oferta",
                Map.of("precio", 1.50, "desde", hoy.toString(), "hasta", hoy.plusDays(3).toString()))
                .andExpect(status().isOk());
        putCon(admin, "/api/productos/" + perno.getId() + "/presentaciones/" + datos.presentacionId(perno, 0) + "/escalas",
                Map.of("escalas", List.of(Map.of("cantidadMinima", 100, "precio", 0.40)))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("La lista de precios agrupa por categoria y marca ofertas y precio por mayor; la ve el VENDEDOR")
    void listaPrecios() throws Exception {
        byte[] pdf = getCon(vendedor, "/api/productos/lista-precios")
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        String texto = texto(pdf);
        assertThat(texto).contains("LISTA DE PRECIOS", "PER-1", "UNIDAD: S/ 0.50", "Ciento: S/ 40.00",
                "Por mayor: desde 100 a S/ 0.40", "S/ 1.50 OFERTA");
        getCon(vendedor, "/api/productos/lista-precios?categoriaId=999999").andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Las etiquetas salen por la presentacion principal (o todas) y por copias, con limite")
    void etiquetas() throws Exception {
        byte[] pdf = getCon(admin, "/api/productos/etiquetas?productoIds={a},{b}&copias=2", perno.getId(), lija.getId())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        String texto = texto(pdf);
        assertThat(texto).contains("S/ 0.50", "S/ 1.50", "OFERTA");
        assertThat(texto.split("S/ 0.50", -1)).hasSize(3); // 2 copias del perno
        assertThat(texto(getCon(admin, "/api/productos/etiquetas?productoIds={a}&todas=true", perno.getId())
                .andReturn().getResponse().getContentAsByteArray())).contains("Ciento", "S/ 40.00");
        getCon(admin, "/api/productos/etiquetas?productoIds={a}&copias=51", perno.getId())
                .andExpect(status().isUnprocessableContent());
    }

    private static String texto(byte[] pdf) throws IOException {
        try (PdfReader lector = new PdfReader(pdf)) {
            StringBuilder texto = new StringBuilder();
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                texto.append(new PdfTextExtractor(lector).getTextFromPage(pagina)).append('\n');
            }
            return texto.toString();
        }
    }
}
