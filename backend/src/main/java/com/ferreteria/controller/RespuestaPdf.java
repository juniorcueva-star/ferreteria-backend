package com.ferreteria.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Respuesta HTTP con un PDF: para descargar (attachment) o para verlo e imprimirlo en el navegador (inline). */
final class RespuestaPdf {

    private RespuestaPdf() {
    }

    static ResponseEntity<byte[]> de(byte[] pdf, String nombre, boolean descargar) {
        ContentDisposition disposicion = (descargar ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(nombre).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(pdf);
    }
}
