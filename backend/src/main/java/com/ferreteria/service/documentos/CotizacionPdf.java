package com.ferreteria.service.documentos;

import com.ferreteria.entity.Cotizacion;
import com.ferreteria.entity.CotizacionDetalle;
import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
import lombok.RequiredArgsConstructor;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.ferreteria.service.documentos.MembretePdf.soles;

/**
 * PDF de una cotizacion (OpenPDF, licencia LGPL/MPL) con el membrete del negocio: cliente, detalle, subtotal, IGV,
 * total y cuentas bancarias. Es el mismo archivo que se descarga, se imprime y se adjunta al correo.
 */
@Component
@RequiredArgsConstructor
public class CotizacionPdf {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final MembretePdf membrete;

    public byte[] generar(Cotizacion cotizacion) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.addTitle("Cotización " + cotizacion.getCodigo());
            documento.open();
            membrete.encabezado(documento, cotizacion.getUbicacion().getEmpresa(), cotizacion.getUbicacion(),
                    "COTIZACIÓN", cotizacion.getCodigo());
            datos(documento, cotizacion);
            detalle(documento, cotizacion);
            totales(documento, cotizacion);
            if (cotizacion.getObservacion() != null) {
                Paragraph observacion = new Paragraph("Observación: " + cotizacion.getObservacion(), membrete.normal);
                observacion.setSpacingBefore(10);
                documento.add(observacion);
            }
            membrete.pie(documento, cotizacion.getUbicacion().getEmpresa(), "Precios en soles e incluyen IGV. "
                    + "Sujeto a disponibilidad de stock. Este documento no es un comprobante de pago.");
            documento.close();
        } catch (DocumentException e) {
            throw new ApiException(CodigoError.ERROR_INTERNO, "No se pudo generar el PDF de la cotizacion");
        }
        return salida.toByteArray();
    }

    private void datos(Document documento, Cotizacion c) throws DocumentException {
        List<String[]> pares = new ArrayList<>();
        pares.add(new String[]{"Fecha:", FECHA.format(c.getCreatedAt().atZoneSameInstant(LIMA))});
        pares.add(new String[]{"Válida hasta:", FECHA.format(c.getFechaValidez())});
        if (c.getCliente() == null) {
            pares.add(new String[]{"Cliente:", "Cliente varios"});
        } else {
            pares.add(new String[]{"Cliente:", c.getCliente().getNombre()});
            if (c.getCliente().getNumeroDocumento() != null) {
                pares.add(new String[]{c.getCliente().getTipoDocumento() + ":", c.getCliente().getNumeroDocumento()});
            }
        }
        pares.add(new String[]{"Atendido por:", c.getUsuario().getNombres()});
        membrete.datos(documento, pares);
    }

    private void detalle(Document documento, Cotizacion c) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[]{1.2f, 6, 1.8f, 1.6f, 2});
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(10);
        tabla.setHeaderRows(1);
        for (String columna : new String[]{"Cant.", "Descripción", "P. unit.", "Desc.", "Importe"}) {
            tabla.addCell(membrete.titulo(columna));
        }
        for (CotizacionDetalle d : c.getDetalles()) {
            tabla.addCell(membrete.celda(d.getCantidad().stripTrailingZeros().toPlainString(), Element.ALIGN_RIGHT));
            tabla.addCell(membrete.celdaDescripcion(d.getDescripcion(), d.getComposicion(), d.getTipoPrecio()));
            tabla.addCell(membrete.celda(soles(d.getPrecioUnitario()), Element.ALIGN_RIGHT));
            tabla.addCell(membrete.celda(d.getDescuento().signum() == 0 ? "" : soles(d.getDescuento()),
                    Element.ALIGN_RIGHT));
            tabla.addCell(membrete.celda(soles(d.getSubtotal()), Element.ALIGN_RIGHT));
        }
        documento.add(tabla);
    }

    private void totales(Document documento, Cotizacion c) throws DocumentException {
        List<String[]> filas = new ArrayList<>();
        filas.add(new String[]{"Op. gravada", soles(c.getSubtotal())});
        filas.add(new String[]{"IGV (18 %)", soles(c.getIgv())});
        if (c.getDescuento().signum() > 0) {
            filas.add(new String[]{"Descuentos aplicados", soles(c.getDescuento())});
        }
        filas.add(new String[]{"TOTAL", soles(c.getTotal())});
        membrete.totales(documento, filas);
    }
}
