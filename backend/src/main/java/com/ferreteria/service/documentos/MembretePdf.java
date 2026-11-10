package com.ferreteria.service.documentos;

import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.Ubicacion;
import lombok.extern.slf4j.Slf4j;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Image;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Membrete comun de los documentos impresos (cotizacion y nota de venta), con los colores del negocio (amarillo,
 * negro y blanco): logo, nombre comercial, lema, razon social, tienda, telefono y un recuadro con el RUC, el tipo de
 * documento y su numero. Al pie, las cuentas bancarias de la empresa. El logo esta en {@code documentos/logo.png};
 * si falta, el documento sale igual sin logo.
 */
@Slf4j
@Component
public class MembretePdf {

    static final Color AMARILLO = new Color(245, 196, 0);
    static final Color AMARILLO_CLARO = new Color(255, 246, 204);
    static final Color NEGRO = new Color(17, 17, 17);
    static final Color GRIS = new Color(82, 82, 82);

    final Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, NEGRO);
    final Font negrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, NEGRO);
    final Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9, NEGRO);
    final Font pequena = FontFactory.getFont(FontFactory.HELVETICA, 8, GRIS);
    private final Font nombreComercial = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, NEGRO);
    private final Font lema = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(150, 115, 0));
    private final Font recuadroRuc = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, NEGRO);
    private final Font recuadroTipo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, NEGRO);

    private final byte[] logo;

    public MembretePdf() {
        this.logo = leerLogo();
    }

    /**
     * Encabezado del documento.
     *
     * @param tipo   Ej: "COTIZACIÓN" o "NOTA DE VENTA"
     * @param numero Ej: "COT-000012" o "NV01-000245"
     */
    public void encabezado(Document documento, Empresa empresa, Ubicacion tienda, String tipo, String numero)
            throws DocumentException {
        PdfPTable tabla = new PdfPTable(logo == null ? new float[]{5.2f, 2.8f} : new float[]{1.25f, 4f, 2.75f});
        tabla.setWidthPercentage(100);

        if (logo != null) {
            PdfPCell celdaLogo = sinBorde();
            try {
                Image imagen = Image.getInstance(logo);
                imagen.scaleToFit(68, 68);
                celdaLogo.addElement(imagen);
            } catch (IOException e) {
                log.warn("No se pudo leer el logo de los documentos: {}", e.getMessage());
            }
            tabla.addCell(celdaLogo);
        }

        PdfPCell datos = sinBorde();
        datos.setPaddingLeft(4);
        String nombre = empresa.getNombreComercial() == null ? empresa.getRazonSocial() : empresa.getNombreComercial();
        datos.addElement(sinEspacio(new Paragraph(nombre, nombreComercial)));
        if (empresa.getLema() != null) {
            datos.addElement(sinEspacio(new Paragraph(empresa.getLema().toUpperCase(Locale.ROOT), lema)));
        }
        if (empresa.getNombreComercial() != null) {
            datos.addElement(sinEspacio(new Paragraph(empresa.getRazonSocial(), normal)));
        }
        String lugar = tienda.getNombre() + (tienda.getDireccion() == null ? "" : " · " + tienda.getDireccion());
        datos.addElement(sinEspacio(new Paragraph(lugar, normal)));
        if (empresa.getDireccion() != null && !empresa.getDireccion().equals(tienda.getDireccion())) {
            datos.addElement(sinEspacio(new Paragraph("Dirección fiscal: " + empresa.getDireccion(), pequena)));
        }
        if (empresa.getTelefono() != null) {
            datos.addElement(sinEspacio(new Paragraph("WhatsApp / Teléfono: " + empresa.getTelefono(), negrita)));
        }
        tabla.addCell(datos);

        PdfPTable recuadro = new PdfPTable(1);
        recuadro.addCell(celdaRecuadro("R.U.C. " + empresa.getRuc(), recuadroRuc, Color.WHITE));
        recuadro.addCell(celdaRecuadro(tipo, recuadroTipo, AMARILLO));
        recuadro.addCell(celdaRecuadro(numero, recuadroRuc, Color.WHITE));
        PdfPCell contenedor = new PdfPCell(recuadro);
        contenedor.setBorderColor(NEGRO);
        contenedor.setBorderWidth(1.5f);
        contenedor.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(contenedor);
        documento.add(tabla);
        franja(documento);
    }

    /** Linea de datos (fecha, cliente, vendedor...) en dos columnas debajo del encabezado. */
    public void datos(Document documento, List<String[]> pares) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[]{1.3f, 3.2f, 1.3f, 3.2f});
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(6);
        for (String[] par : pares) {
            PdfPCell etiqueta = sinBorde();
            etiqueta.setPhrase(new Phrase(par[0], negrita));
            etiqueta.setPadding(2);
            PdfPCell valor = sinBorde();
            valor.setPhrase(new Phrase(par[1], normal));
            valor.setPadding(2);
            tabla.addCell(etiqueta);
            tabla.addCell(valor);
        }
        tabla.completeRow();
        documento.add(tabla);
    }

    /** Fila de titulos de una tabla de detalle, en amarillo claro. */
    public PdfPCell titulo(String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, negrita));
        celda.setBackgroundColor(AMARILLO);
        celda.setBorderColor(NEGRO);
        celda.setPadding(5);
        return celda;
    }

    public PdfPCell celda(String texto, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, normal));
        celda.setHorizontalAlignment(alineacion);
        celda.setBorderColor(new Color(212, 212, 212));
        celda.setPadding(4);
        return celda;
    }

    /** Descripcion de una linea; en un completo, debajo y en letra pequena, sus piezas. */
    public PdfPCell celdaDescripcion(String descripcion, String composicion) {
        if (composicion == null) {
            return celda(descripcion, Element.ALIGN_LEFT);
        }
        Phrase texto = new Phrase(descripcion, normal);
        texto.add(new Phrase("\nIncluye: " + composicion, pequena));
        PdfPCell celda = new PdfPCell(texto);
        celda.setBorderColor(new Color(212, 212, 212));
        celda.setPadding(4);
        return celda;
    }

    /** Pie: cuentas bancarias (si la empresa las tiene) y una nota. */
    public void pie(Document documento, Empresa empresa, String nota) throws DocumentException {
        if (empresa.getDatosBancarios() != null) {
            PdfPTable tabla = new PdfPTable(1);
            tabla.setWidthPercentage(100);
            tabla.setSpacingBefore(14);
            PdfPCell celda = new PdfPCell();
            celda.setBackgroundColor(AMARILLO_CLARO);
            celda.setBorderColor(AMARILLO);
            celda.setPadding(6);
            celda.addElement(sinEspacio(new Paragraph("Cuentas para depósito", negrita)));
            for (String linea : empresa.getDatosBancarios().split("\\R")) {
                if (!linea.isBlank()) {
                    celda.addElement(sinEspacio(new Paragraph(linea.trim(), normal)));
                }
            }
            tabla.addCell(celda);
            documento.add(tabla);
        }
        Paragraph texto = new Paragraph(nota, pequena);
        texto.setSpacingBefore(10);
        documento.add(texto);
        Paragraph gracias = new Paragraph("¡Gracias por su preferencia!", negrita);
        gracias.setAlignment(Element.ALIGN_CENTER);
        gracias.setSpacingBefore(8);
        documento.add(gracias);
    }

    /** Tabla de totales alineada a la derecha; la ultima fila (TOTAL) va resaltada en amarillo. */
    public void totales(Document documento, List<String[]> filas) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[]{3, 2});
        tabla.setWidthPercentage(42);
        tabla.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tabla.setSpacingBefore(8);
        for (int i = 0; i < filas.size(); i++) {
            boolean total = i == filas.size() - 1;
            Font fuente = total ? recuadroTipo : normal;
            PdfPCell izquierda = new PdfPCell(new Phrase(filas.get(i)[0], fuente));
            PdfPCell derecha = new PdfPCell(new Phrase(filas.get(i)[1], fuente));
            derecha.setHorizontalAlignment(Element.ALIGN_RIGHT);
            for (PdfPCell celda : new PdfPCell[]{izquierda, derecha}) {
                celda.setBorder(Rectangle.NO_BORDER);
                celda.setPadding(3);
                if (total) {
                    celda.setBackgroundColor(AMARILLO);
                    celda.setPadding(5);
                }
                tabla.addCell(celda);
            }
        }
        documento.add(tabla);
    }

    public static String soles(BigDecimal monto) {
        return String.format(Locale.US, "S/ %,.2f", monto);
    }

    private void franja(Document documento) throws DocumentException {
        PdfPTable franja = new PdfPTable(1);
        franja.setWidthPercentage(100);
        franja.setSpacingBefore(6);
        PdfPCell celda = new PdfPCell();
        celda.setFixedHeight(3);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setBackgroundColor(AMARILLO);
        franja.addCell(celda);
        documento.add(franja);
    }

    private static PdfPCell celdaRecuadro(String texto, Font fuente, Color fondo) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setBackgroundColor(fondo);
        celda.setPadding(5);
        return celda;
    }

    private static PdfPCell sinBorde() {
        PdfPCell celda = new PdfPCell();
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return celda;
    }

    private static Paragraph sinEspacio(Paragraph parrafo) {
        parrafo.setLeading(parrafo.getFont().getSize() * 1.25f);
        return parrafo;
    }

    private static byte[] leerLogo() {
        ClassPathResource recurso = new ClassPathResource("documentos/logo.png");
        if (!recurso.exists()) {
            return null;
        }
        try (InputStream entrada = recurso.getInputStream()) {
            return entrada.readAllBytes();
        } catch (IOException e) {
            log.warn("No se pudo leer el logo de los documentos: {}", e.getMessage());
            return null;
        }
    }
}
