package com.ferreteria.integracion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CajaClienteIntegrationTest extends IntegracionTestBase {

    @Test
    @DisplayName("El vendedor abre su caja en su tienda, solo una a la vez, y la cierra con cuadre")
    void abrirYCerrarSinVentas() throws Exception {
        String token = datos.token(datos.vendedor1);
        Long cajaId = leerId(postCon(token, "/api/cajas/abrir", Map.of("montoApertura", 100))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ubicacionNombre").value("Tienda 1"))
                .andExpect(jsonPath("$.estado").value("ABIERTA"))
                .andExpect(jsonPath("$.resumen.efectivoEsperado").value(100.0))
                .andReturn(), "$.id");

        postCon(token, "/api/cajas/abrir", Map.of("montoApertura", 50))
                .andExpect(status().isUnprocessableContent());
        getCon(token, "/api/cajas/actual").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(cajaId));

        // Otro vendedor no puede cerrarla
        postCon(datos.token(datos.vendedor2), "/api/cajas/{id}/cerrar", Map.of("efectivoContado", 0), cajaId)
                .andExpect(status().isForbidden());

        postCon(token, "/api/cajas/{id}/cerrar", Map.of("efectivoContado", 95.50), cajaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA"))
                .andExpect(jsonPath("$.efectivoEsperado").value(100.0))
                .andExpect(jsonPath("$.efectivoContado").value(95.5))
                .andExpect(jsonPath("$.diferencia").value(-4.5));
        postCon(token, "/api/cajas/{id}/cerrar", Map.of("efectivoContado", 100), cajaId)
                .andExpect(status().isUnprocessableContent());
        getCon(token, "/api/cajas/actual").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Reglas de apertura: solo en tiendas, el vendedor solo en la suya, el almacenero no usa caja")
    void reglasDeApertura() throws Exception {
        postCon(datos.token(datos.vendedor1), "/api/cajas/abrir",
                Map.of("ubicacionId", datos.tienda2.getId(), "montoApertura", 0))
                .andExpect(status().isForbidden());
        postCon(datos.token(datos.admin), "/api/cajas/abrir",
                Map.of("ubicacionId", datos.almacen.getId(), "montoApertura", 0))
                .andExpect(status().isUnprocessableContent());
        postCon(datos.token(datos.almacenero), "/api/cajas/abrir", Map.of("montoApertura", 0))
                .andExpect(status().isForbidden());
        postCon(datos.token(datos.admin), "/api/cajas/abrir",
                Map.of("ubicacionId", datos.tienda2.getId(), "montoApertura", 0))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Un vendedor no ve cajas de otra tienda")
    void cajasDeOtraTienda() throws Exception {
        Long cajaId = leerId(postCon(datos.token(datos.vendedor2), "/api/cajas/abrir", Map.of("montoApertura", 10))
                .andReturn(), "$.id");
        getCon(datos.token(datos.vendedor1), "/api/cajas/{id}", cajaId).andExpect(status().isForbidden());
        getCon(datos.token(datos.vendedor1), "/api/cajas").andExpect(jsonPath("$.totalElementos").value(0));
        getCon(datos.token(datos.admin), "/api/cajas").andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @DisplayName("Clientes: el documento se valida segun su tipo y no se repite")
    void clientes() throws Exception {
        String token = datos.token(datos.vendedor1);
        postCon(token, "/api/clientes", Map.of("nombre", "Juan Quispe", "tipoDocumento", "DNI",
                "numeroDocumento", "1234"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value("El DNI debe tener 8 digitos"));
        postCon(token, "/api/clientes", Map.of("nombre", "Juan Quispe", "tipoDocumento", "DNI",
                "numeroDocumento", "45678912", "telefono", "987654321"))
                .andExpect(status().isCreated());
        postCon(token, "/api/clientes", Map.of("nombre", "Otro Juan", "tipoDocumento", "DNI",
                "numeroDocumento", "45678912"))
                .andExpect(status().isConflict());
        postCon(token, "/api/clientes", Map.of("nombre", "Vecino sin documento")).andExpect(status().isCreated());
        getCon(token, "/api/clientes?texto=4567").andExpect(jsonPath("$.totalElementos").value(1));
        getCon(datos.token(datos.almacenero), "/api/clientes").andExpect(status().isForbidden());
    }
}
