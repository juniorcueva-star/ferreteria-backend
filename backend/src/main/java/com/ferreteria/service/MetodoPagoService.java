package com.ferreteria.service;

import com.ferreteria.dto.comun.PaginaResponse;
import com.ferreteria.dto.ventas.MetodoPagoResponse;
import com.ferreteria.repository.MetodoPagoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetodoPagoService {

    private final MetodoPagoRepository metodoPagoRepository;

    @Transactional(readOnly = true)
    public PaginaResponse<MetodoPagoResponse> listar(Pageable pageable) {
        return PaginaResponse.de(metodoPagoRepository.findAll(Ordenamiento.validar(pageable, "id", "codigo", "nombre")), MetodoPagoResponse::desde);
    }
}
