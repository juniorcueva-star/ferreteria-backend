package com.ferreteria.service;

import com.ferreteria.dto.clientes.ClienteRequest;
import com.ferreteria.dto.clientes.ClienteResponse;
import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.entity.Cliente;
import com.ferreteria.entity.enums.TipoDocumentoIdentidad;
import com.ferreteria.exception.DuplicadoException;
import com.ferreteria.exception.RecursoNoEncontradoException;
import com.ferreteria.exception.ReglaNegocioException;
import com.ferreteria.repository.ClienteRepository;
import com.ferreteria.repository.Especificaciones;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clientes (sobre todo para el fiado). Son comunes a las dos tiendas: un cliente puede comprar en ambas.
 */
@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;

    @Transactional(readOnly = true)
    public PaginaResponse<ClienteResponse> listar(String texto, Boolean activo, Pageable pageable) {
        Specification<Cliente> filtro = Specification.allOf(
                Especificaciones.contiene(texto, "nombre", "numeroDocumento", "telefono"),
                Especificaciones.igual("activo", activo));
        return PaginaResponse.de(clienteRepository.findAll(filtro, pageable), ClienteResponse::desde);
    }

    @Transactional(readOnly = true)
    public ClienteResponse obtener(Long id) {
        return ClienteResponse.desde(buscar(id));
    }

    @Transactional
    public ClienteResponse crear(ClienteRequest request) {
        Cliente cliente = new Cliente();
        copiarDatos(request, cliente, null);
        return ClienteResponse.desde(clienteRepository.save(cliente));
    }

    @Transactional
    public ClienteResponse actualizar(Long id, ClienteRequest request) {
        Cliente cliente = buscar(id);
        copiarDatos(request, cliente, id);
        return ClienteResponse.desde(cliente);
    }

    private void copiarDatos(ClienteRequest request, Cliente cliente, Long id) {
        TipoDocumentoIdentidad tipo = request.tipoDocumento() == null
                ? TipoDocumentoIdentidad.NINGUNO : request.tipoDocumento();
        String numero = request.numeroDocumento() == null || request.numeroDocumento().isBlank()
                ? null : request.numeroDocumento().trim().toUpperCase();
        validarDocumento(tipo, numero);
        if (numero != null) {
            boolean existe = id == null
                    ? clienteRepository.existsByTipoDocumentoAndNumeroDocumento(tipo, numero)
                    : clienteRepository.existsByTipoDocumentoAndNumeroDocumentoAndIdNot(tipo, numero, id);
            if (existe) {
                throw new DuplicadoException("Ya existe un cliente con " + tipo + " " + numero);
            }
        }
        cliente.setNombre(request.nombre().trim());
        cliente.setTipoDocumento(tipo);
        cliente.setNumeroDocumento(numero);
        cliente.setTelefono(request.telefono());
        cliente.setDireccion(request.direccion());
        if (request.activo() != null) {
            cliente.setActivo(request.activo());
        }
    }

    /** Mismas reglas que el CHECK ck_cliente_doc, pero con un mensaje claro. */
    private static void validarDocumento(TipoDocumentoIdentidad tipo, String numero) {
        boolean valido = switch (tipo) {
            case NINGUNO -> numero == null;
            case DNI -> numero != null && numero.matches("\\d{8}");
            case RUC -> numero != null && numero.matches("\\d{11}");
            case CE -> numero != null;
        };
        if (!valido) {
            throw new ReglaNegocioException(switch (tipo) {
                case NINGUNO -> "Sin tipo de documento no se registra numero (use DNI, RUC o CE)";
                case DNI -> "El DNI debe tener 8 digitos";
                case RUC -> "El RUC debe tener 11 digitos";
                case CE -> "Ingrese el numero de carne de extranjeria";
            });
        }
    }

    private Cliente buscar(Long id) {
        return clienteRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
    }
}
