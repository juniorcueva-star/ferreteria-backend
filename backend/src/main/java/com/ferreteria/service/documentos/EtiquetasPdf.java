package com.ferreteria.service.documentos;

import com.ferreteria.entity.Presentacion;
import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
import lombok.extern.slf4j.Slf4j;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.Barcode128;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static com.ferreteria.service.documentos.MembretePdf.soles;

/**
 * Etiquetas para pegar en los estantes: hoja A4 de 3 x 8 (aprox. 70 x 37 mm), cada una con el producto, la
 * presentacion, el precio (el de oferta si esta vigente) y el codigo de barras Code 128 que lee la caja. Si la
 * presentacion no tiene codigo de barras se usa el codigo del producto. Los bordes grises sirven de guia de corte.
 */
@Slf4j
@Component
public class EtiquetasPdf {

    public static final int COLUMNAS = 3;
    private static final float ALTO = 100;

    private final Font nombre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, MembretePdf.NEGRO);
    private final Font detalle = FontFactory.getFont(FontFactory.HELVETICA, 7, MembretePdf.GRIS);
    private final Font precio = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, MembretePdf.NEGRO);
    private final Font marcaOferta = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(150, 115, 0));

    /** @param etiquetas una presentacion por etiqueta (repetida segun las copias) */
    public byte[] generar(List<Presentacion> etiquetas, String negocio, LocalDate hoy) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 10, 10, 20, 20);
        try {
            PdfWriter writer = PdfWriter.getInstance(documento, salida);
            documento.addTitle("Etiquetas de precios");
            documento.open();
            PdfContentByte lienzo = writer.getDirectContent();
            PdfPTable tabla = new PdfPTable(COLUMNAS);
            tabla.setWidthPercentage(100);
            for (Presentacion p : etiquetas) {
                tabla.addCell(etiqueta(p, negocio, hoy, lienzo));
            }
            tabla.getDefaultCell().setBorder(Rectangle.NO_BORDER);
            tabla.completeRow();
            documento.add(tabla);
            documento.close();
        } catch (DocumentException e) {
            throw new ApiException(CodigoError.ERROR_INTERNO, "No se pudieron generar las etiquetas");
        }
        return salida.toByteArray();
    }

    /** Lo que lee el lector en la caja: el codigo de barras de la presentacion o, si no tiene, el del producto. */
    public static String codigoDe(Presentacion p) {
        return p.getCodigoBarras() != null ? p.getCodigoBarras() : p.getProducto().getCodigo();
    }

    private PdfPCell etiqueta(Presentacion p, String negocio, LocalDate hoy, PdfContentByte lienzo) {
        // Tabla interna de una columna: cada fila ocupa su alto y nada se encima
        PdfPTable contenido = new PdfPTable(1);
        contenido.setWidthPercentage(100);
        contenido.addCell(fila(new Phrase(recortar(p.getProducto().getNombre(), 60), nombre), 0));
        contenido.addCell(fila(new Phrase(p.getNombre() + " · " + negocio, detalle), 2));
        boolean enOferta = p.ofertaVigente(hoy);
        Phrase importe = new Phrase(soles(enOferta ? p.getPrecioOferta() : p.getPrecioVenta()), precio);
        if (enOferta) {
            importe.add(new Chunk("  OFERTA", marcaOferta));
        }
        contenido.addCell(fila(importe, 3));
        PdfPCell barrasCelda;
        try {
            Barcode128 barras = new Barcode128();
            barras.setCode(codigoDe(p));
            barras.setBarHeight(20);
            barras.setX(0.8f);
            barras.setSize(7);
            Image imagen = barras.createImageWithBarcode(lienzo, Color.BLACK, Color.BLACK);
            imagen.scaleToFit(170, 30);
            barrasCelda = new PdfPCell(imagen, false);
        } catch (RuntimeException e) {
            // Un codigo con caracteres que Code 128 no admite: la etiqueta sale sin barras
            log.warn("Etiqueta sin codigo de barras para {}: {}", codigoDe(p), e.getMessage());
            barrasCelda = new PdfPCell(new Phrase(codigoDe(p), detalle));
        }
        barrasCelda.setBorder(Rectangle.NO_BORDER);
        barrasCelda.setHorizontalAlignment(Element.ALIGN_CENTER);
        barrasCelda.setPaddingTop(5);
        contenido.addCell(barrasCelda);

        PdfPCell celda = new PdfPCell(contenido);
        celda.setFixedHeight(ALTO);
        celda.setBorderColor(new Color(200, 200, 200));
        celda.setBorderWidth(0.5f);
        celda.setPadding(5);
        return celda;
    }

    private static PdfPCell fila(Phrase texto, float espacioArriba) {
        PdfPCell celda = new PdfPCell(texto);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(0);
        celda.setPaddingTop(espacioArriba);
        return celda;
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }
}
