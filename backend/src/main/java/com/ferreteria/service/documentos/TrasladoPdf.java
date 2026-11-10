package com.ferreteria.service.documentos;

import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.Traslado;
import com.ferreteria.entity.TrasladoDetalle;
import com.ferreteria.entity.enums.EstadoTraslado;
import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
import lombok.RequiredArgsConstructor;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Guia de traslado: acompana la mercaderia del almacen a la tienda. Lleva el membrete, origen y destino, los
 * productos con la ubicacion fisica en el origen (para alistarlos) y espacio para las firmas de quien entrega y
 * quien recibe.
 */
@Component
@RequiredArgsConstructor
public class TrasladoPdf {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final MembretePdf membrete;

    /**
     * @param empresa           empresa del membrete (la de la tienda destino o la del origen)
     * @param ubicacionesOrigen productoId -> ubicacion fisica en el origen (Ej: "Pasillo 3 - Estante B")
     */
    public byte[] generar(Traslado traslado, Empresa empresa, Map<Long, String> ubicacionesOrigen) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.addTitle("Guía de traslado " + traslado.getCodigo());
            documento.open();
            membrete.encabezado(documento, empresa, traslado.getOrigen(), "GUÍA DE TRASLADO", traslado.getCodigo());
            datos(documento, traslado);
            detalle(documento, traslado, ubicacionesOrigen);
            if (traslado.getObservacion() != null) {
                Paragraph observacion = new Paragraph("Observación: " + traslado.getObservacion(), membrete.normal);
                observacion.setSpacingBefore(10);
                documento.add(observacion);
            }
            firmas(documento);
            Paragraph nota = new Paragraph("Documento interno de traslado entre ubicaciones de la empresa. Revise la "
                    + "mercadería al recibirla: la diferencia se registra con un ajuste.", membrete.pequena);
            nota.setSpacingBefore(14);
            documento.add(nota);
            documento.close();
        } catch (DocumentException e) {
            throw new ApiException(CodigoError.ERROR_INTERNO, "No se pudo generar la guia de traslado");
        }
        return salida.toByteArray();
    }

    private void datos(Document documento, Traslado t) throws DocumentException {
        List<String[]> pares = new ArrayList<>();
        pares.add(new String[]{"Origen:", t.getOrigen().getNombre()});
        pares.add(new String[]{"Destino:", t.getDestino().getNombre()
                + (t.getDestino().getDireccion() == null ? "" : " · " + t.getDestino().getDireccion())});
        pares.add(new String[]{"Enviado:", FECHA_HORA.format(t.getFechaEnvio().atZoneSameInstant(LIMA))});
        pares.add(new String[]{"Envía:", t.getUsuarioEnvia().getNombres()});
        pares.add(new String[]{"Estado:", switch (t.getEstado()) {
            case ENVIADO -> "En camino";
            case RECIBIDO -> "Recibido";
            case ANULADO -> "Anulado";
        }});
        if (t.getEstado() == EstadoTraslado.RECIBIDO && t.getUsuarioRecibe() != null) {
            pares.add(new String[]{"Recibió:", t.getUsuarioRecibe().getNombres() + " · "
                    + FECHA_HORA.format(t.getFechaRecepcion().atZoneSameInstant(LIMA))});
        }
        membrete.datos(documento, pares);
    }

    private void detalle(Document documento, Traslado t, Map<Long, String> ubicaciones) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[]{1.3f, 2.2f, 6, 2, 3});
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(10);
        tabla.setHeaderRows(1);
        for (String columna : new String[]{"Cant.", "Presentación", "Producto", "En unidad base", "Ubicación en origen"}) {
            tabla.addCell(membrete.titulo(columna));
        }
        for (TrasladoDetalle d : t.getDetalles()) {
            tabla.addCell(membrete.celda(numero(d.getCantidad()), Element.ALIGN_RIGHT));
            tabla.addCell(membrete.celda(d.getPresentacion() == null ? "Unidad base" : d.getPresentacion().getNombre(),
                    Element.ALIGN_LEFT));
            tabla.addCell(membrete.celda(d.getProducto().getCodigo() + " · " + d.getProducto().getNombre(),
                    Element.ALIGN_LEFT));
            tabla.addCell(membrete.celda(numero(d.getCantidadBase()) + " " + switch (d.getProducto().getUnidadBase()) {
                case UNIDAD -> "u";
                case KILO -> "kg";
                case METRO -> "m";
            }, Element.ALIGN_RIGHT));
            tabla.addCell(membrete.celda(ubicaciones.getOrDefault(d.getProducto().getId(), ""), Element.ALIGN_LEFT));
        }
        documento.add(tabla);
        Paragraph total = new Paragraph(t.getDetalles().size() + " producto(s)", membrete.negrita);
        total.setAlignment(Element.ALIGN_RIGHT);
        total.setSpacingBefore(4);
        documento.add(total);
    }

    private void firmas(Document documento) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[]{1, 0.3f, 1});
        tabla.setWidthPercentage(90);
        tabla.setSpacingBefore(50);
        tabla.addCell(firma("Entregué conforme (almacén)"));
        PdfPCell espacio = new PdfPCell();
        espacio.setBorder(Rectangle.NO_BORDER);
        tabla.addCell(espacio);
        tabla.addCell(firma("Recibí conforme (tienda)"));
        documento.add(tabla);
    }

    private PdfPCell firma(String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto + "\nNombre y DNI:", membrete.normal));
        celda.setBorder(Rectangle.TOP);
        celda.setBorderWidthTop(1);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        celda.setPaddingTop(4);
        return celda;
    }

    private static String numero(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }
}
