package com.ferreteria.service;

import com.ferreteria.dto.catalogo.UnidadMedidaResponse;
import com.ferreteria.repository.UnidadMedidaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Catalogo de unidades de venta para asignar a los productos. */
@Service
@RequiredArgsConstructor
public class UnidadMedidaService {

    private final UnidadMedidaRepository unidadMedidaRepository;

    @Transactional(readOnly = true)
    public List<UnidadMedidaResponse> listarActivas() {
        return unidadMedidaRepository.findByActivoTrueOrderByIdAsc().stream()
                .map(UnidadMedidaResponse::desde)
                .toList();
    }
}
