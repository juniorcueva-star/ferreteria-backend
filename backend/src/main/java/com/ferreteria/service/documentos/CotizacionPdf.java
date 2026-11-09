package com.ferreteria.service.documentos;

import com.ferreteria.entity.Cotizacion;
import com.ferreteria.entity.CotizacionDetalle;
import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * PDF de una cotizacion (OpenPDF, licencia LGPL/MPL): datos de la empresa y la tienda, cliente, detalle, subtotal,
 * IGV y total. Es el mismo archivo que se descarga, se imprime y se adjunta al correo.
 */
@Component
public class CotizacionPdf {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Color GRIS = new Color(240, 236, 228);

    private final Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private final Font negrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private final Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private final Font pequena = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);

    public byte[] generar(Cotizacion cotizacion) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.addTitle("Cotización " + cotizacion.getCodigo());
            documento.open();
            encabezado(documento, cotizacion);
            detalle(documento, cotizacion);
            totales(documento, cotizacion);
            pie(documento, cotizacion);
            documento.close();
        } catch (DocumentException e) {
            throw new ApiException(CodigoError.ERROR_INTERNO, "No se pudo generar el PDF de la cotizacion");
        }
        return salida.toByteArray();
    }

    private void encabezado(Document documento, Cotizacion c) throws DocumentException {
        Ubicacion tienda = c.getUbicacion();
        Empresa empresa = tienda.getEmpresa();
        PdfPTable tabla = new PdfPTable(new float[]{3, 2});
        tabla.setWidthPercentage(100);

        PdfPCell datosEmpresa = celdaSinBorde();
        datosEmpresa.addElement(new Paragraph(empresa.getRazonSocial(), titulo));
        datosEmpresa.addElement(new Paragraph("RUC " + empresa.getRuc(), negrita));
        datosEmpresa.addElement(new Paragraph(tienda.getNombre()
                + (tienda.getDireccion() == null ? "" : " · " + tienda.getDireccion()), normal));
        tabla.addCell(datosEmpresa);

        PdfPCell recuadro = new PdfPCell();
        recuadro.setPadding(8);
        recuadro.setBackgroundColor(GRIS);
        recuadro.addElement(centrado("COTIZACIÓN", titulo));
        recuadro.addElement(centrado(c.getCodigo(), negrita));
        recuadro.addElement(centrado("Fecha: " + FECHA.format(c.getCreatedAt().atZoneSameInstant(LIMA)), normal));
        recuadro.addElement(centrado("Válida hasta: " + FECHA.format(c.getFechaValidez()), normal));
        tabla.addCell(recuadro);
        documento.add(tabla);

        Paragraph cliente = new Paragraph();
        cliente.setSpacingBefore(10);
        cliente.add(new Phrase("Cliente: ", negrita));
        if (c.getCliente() == null) {
            cliente.add(new Phrase("Cliente varios", normal));
        } else {
            cliente.add(new Phrase(c.getCliente().getNombre(), normal));
            if (c.getCliente().getNumeroDocumento() != null) {
                cliente.add(new Phrase("   " + c.getCliente().getTipoDocumento() + ": "
                        + c.getCliente().getNumeroDocumento(), normal));
            }
        }
        cliente.add(new Phrase("   Atendido por: " + c.getUsuario().getNombres(), normal));
        documento.add(cliente);
    }

    private void detalle(Document documento, Cotizacion c) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[]{1.2f, 6, 1.8f, 1.6f, 2});
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(12);
        tabla.setHeaderRows(1);
        for (String columna : new String[]{"Cant.", "Descripción", "P. unit.", "Desc.", "Importe"}) {
            PdfPCell celda = new PdfPCell(new Phrase(columna, negrita));
            celda.setBackgroundColor(GRIS);
            celda.setPadding(5);
            tabla.addCell(celda);
        }
        for (CotizacionDetalle d : c.getDetalles()) {
            tabla.addCell(celda(d.getCantidad().stripTrailingZeros().toPlainString(), Element.ALIGN_RIGHT));
            tabla.addCell(celda(d.getDescripcion(), Element.ALIGN_LEFT));
            tabla.addCell(celda(soles(d.getPrecioUnitario()), Element.ALIGN_RIGHT));
            tabla.addCell(celda(d.getDescuento().signum() == 0 ? "" : soles(d.getDescuento()), Element.ALIGN_RIGHT));
            tabla.addCell(celda(soles(d.getSubtotal()), Element.ALIGN_RIGHT));
        }
        documento.add(tabla);
    }

    private void totales(Document documento, Cotizacion c) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[]{3, 2});
        tabla.setWidthPercentage(40);
        tabla.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tabla.setSpacingBefore(8);
        fila(tabla, "Op. gravada", soles(c.getSubtotal()), normal);
        fila(tabla, "IGV (18 %)", soles(c.getIgv()), normal);
        if (c.getDescuento().signum() > 0) {
            fila(tabla, "Descuentos aplicados", soles(c.getDescuento()), normal);
        }
        fila(tabla, "TOTAL", soles(c.getTotal()), titulo);
        documento.add(tabla);
    }

    private void pie(Document documento, Cotizacion c) throws DocumentException {
        if (c.getObservacion() != null) {
            Paragraph observacion = new Paragraph("Observación: " + c.getObservacion(), normal);
            observacion.setSpacingBefore(12);
            documento.add(observacion);
        }
        Paragraph nota = new Paragraph("Precios en soles e incluyen IGV. Sujeto a disponibilidad de stock. "
                + "Este documento no es un comprobante de pago.", pequena);
        nota.setSpacingBefore(16);
        documento.add(nota);
    }

    private PdfPCell celda(String texto, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, normal));
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(4);
        return celda;
    }

    private static void fila(PdfPTable tabla, String etiqueta, String valor, Font fuente) {
        PdfPCell izquierda = new PdfPCell(new Phrase(etiqueta, fuente));
        PdfPCell derecha = new PdfPCell(new Phrase(valor, fuente));
        derecha.setHorizontalAlignment(Element.ALIGN_RIGHT);
        for (PdfPCell celda : new PdfPCell[]{izquierda, derecha}) {
            celda.setBorder(PdfPCell.NO_BORDER);
            celda.setPadding(3);
            tabla.addCell(celda);
        }
    }

    private static PdfPCell celdaSinBorde() {
        PdfPCell celda = new PdfPCell();
        celda.setBorder(PdfPCell.NO_BORDER);
        return celda;
    }

    private static Paragraph centrado(String texto, Font fuente) {
        Paragraph parrafo = new Paragraph(texto, fuente);
        parrafo.setAlignment(Element.ALIGN_CENTER);
        return parrafo;
    }

    static String soles(BigDecimal monto) {
        return String.format(Locale.US, "S/ %,.2f", monto);
    }
}
