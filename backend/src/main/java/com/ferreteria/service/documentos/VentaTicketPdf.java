package com.ferreteria.service.documentos;

import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.Pago;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.entity.Venta;
import com.ferreteria.entity.VentaDetalle;
import com.ferreteria.entity.enums.CondicionVenta;
import com.ferreteria.entity.enums.EstadoPago;
import com.ferreteria.entity.enums.EstadoVenta;
import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
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
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.ferreteria.service.documentos.MembretePdf.soles;

/**
 * Ticket de la nota de venta para tiquetera termica de 80 mm: una columna angosta con logo, datos de la empresa,
 * productos, totales, pagos y vuelto. El largo de la pagina se calcula segun la cantidad de lineas.
 */
@Slf4j
@Component
public class VentaTicketPdf {

    /** 80 mm de ancho en puntos (1 mm = 72 / 25.4 puntos) */
    static final float ANCHO = 80 * 72 / 25.4f;
    private static final float MARGEN = 8;
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
    private final Font negrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);
    private final Font normal = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
    private final Font pequena = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.DARK_GRAY);
    private final byte[] logo = leerLogo();

    public byte[] generar(Venta venta) {
        List<Pago> pagos = venta.getPagos().stream().filter(p -> p.getEstado() == EstadoPago.VALIDO).toList();
        long conMarca = venta.getDetalles().stream().filter(d -> MembretePdf.marcaPrecio(d.getTipoPrecio()) != null).count();
        float alto = 330 + venta.getDetalles().size() * 30f + conMarca * 10f + pagos.size() * 12f
                + (venta.getEmpresa().getDatosBancarios() == null ? 0
                : venta.getEmpresa().getDatosBancarios().split("\\R").length * 10f + 14);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(new Rectangle(ANCHO, alto), MARGEN, MARGEN, MARGEN, MARGEN);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.addTitle("Ticket " + venta.getNumeroDocumento());
            documento.open();
            cabecera(documento, venta.getEmpresa(), venta.getUbicacion());
            linea(documento);
            centrado(documento, "NOTA DE VENTA", titulo);
            centrado(documento, venta.getNumeroDocumento(), negrita);
            if (venta.getEstado() == EstadoVenta.ANULADA) {
                centrado(documento, "*** ANULADA ***", titulo);
            }
            texto(documento, "Fecha: " + FECHA_HORA.format(venta.getFecha().atZoneSameInstant(LIMA)), normal);
            texto(documento, "Cliente: " + (venta.getCliente() == null ? "Cliente varios" : venta.getCliente().getNombre()
                    + (venta.getCliente().getNumeroDocumento() == null ? ""
                    : " (" + venta.getCliente().getTipoDocumento() + " " + venta.getCliente().getNumeroDocumento() + ")")),
                    normal);
            texto(documento, "Condición: " + (venta.getCondicion() == CondicionVenta.CONTADO ? "Contado" : "Crédito")
                    + " · Vendedor: " + venta.getUsuario().getNombres(), normal);
            linea(documento);
            detalle(documento, venta);
            linea(documento);
            totales(documento, venta, pagos);
            if (venta.getEmpresa().getDatosBancarios() != null) {
                linea(documento);
                texto(documento, "Cuentas para depósito", negrita);
                for (String cuenta : venta.getEmpresa().getDatosBancarios().split("\\R")) {
                    if (!cuenta.isBlank()) {
                        texto(documento, cuenta.trim(), pequena);
                    }
                }
            }
            linea(documento);
            centrado(documento, "¡Gracias por su preferencia!", negrita);
            centrado(documento, "Nota de venta interna: no reemplaza a la boleta o factura electrónica.", pequena);
            documento.close();
        } catch (DocumentException e) {
            throw new ApiException(CodigoError.ERROR_INTERNO, "No se pudo generar el ticket");
        }
        return salida.toByteArray();
    }

    private void cabecera(Document documento, Empresa empresa, Ubicacion tienda) throws DocumentException {
        if (logo != null) {
            try {
                Image imagen = Image.getInstance(logo);
                imagen.scaleToFit(56, 56);
                imagen.setAlignment(Element.ALIGN_CENTER);
                documento.add(imagen);
            } catch (IOException e) {
                log.warn("No se pudo leer el logo del ticket: {}", e.getMessage());
            }
        }
        centrado(documento, empresa.getNombreComercial() == null ? empresa.getRazonSocial() : empresa.getNombreComercial(),
                titulo);
        if (empresa.getLema() != null) {
            centrado(documento, empresa.getLema().toUpperCase(Locale.ROOT), pequena);
        }
        if (empresa.getNombreComercial() != null) {
            centrado(documento, empresa.getRazonSocial(), normal);
        }
        centrado(documento, "R.U.C. " + empresa.getRuc(), negrita);
        centrado(documento, tienda.getNombre() + (tienda.getDireccion() == null ? "" : " · " + tienda.getDireccion()),
                pequena);
        if (empresa.getTelefono() != null) {
            centrado(documento, "WhatsApp: " + empresa.getTelefono(), pequena);
        }
    }

    private void detalle(Document documento, Venta venta) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[]{4, 1.6f});
        tabla.setWidthPercentage(100);
        for (VentaDetalle d : venta.getDetalles()) {
            String cantidad = d.getCantidad().stripTrailingZeros().toPlainString();
            Phrase descripcion = new Phrase(d.getDescripcion(), normal);
            descripcion.add(new Phrase("\n" + cantidad + " x " + soles(d.getPrecioUnitario())
                    + (d.getDescuento().signum() > 0 ? "  desc. " + soles(d.getDescuento()) : ""), pequena));
            if (d.getComposicion() != null) {
                descripcion.add(new Phrase("\nIncluye: " + d.getComposicion(), pequena));
            }
            if (MembretePdf.marcaPrecio(d.getTipoPrecio()) != null) {
                descripcion.add(new Phrase("\n" + MembretePdf.marcaPrecio(d.getTipoPrecio()), negrita));
            }
            tabla.addCell(celda(descripcion, Element.ALIGN_LEFT));
            tabla.addCell(celda(new Phrase(soles(d.getSubtotal()), normal), Element.ALIGN_RIGHT));
        }
        documento.add(tabla);
    }

    private void totales(Document documento, Venta v, List<Pago> pagos) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[]{3, 2});
        tabla.setWidthPercentage(100);
        fila(tabla, "Op. gravada", soles(v.getSubtotal()), normal);
        fila(tabla, "IGV (18 %)", soles(v.getIgv()), normal);
        if (v.getDescuento().signum() > 0) {
            fila(tabla, "Descuentos", soles(v.getDescuento()), normal);
        }
        fila(tabla, "TOTAL", soles(v.getTotal()), titulo);
        for (Pago p : pagos) {
            fila(tabla, p.getMetodoPago().getNombre(), soles(p.getMonto()), normal);
        }
        if (v.getCondicion() == CondicionVenta.CREDITO) {
            fila(tabla, "Saldo pendiente", soles(v.getSaldoPendiente()), negrita);
        }
        documento.add(tabla);
    }

    private static void fila(PdfPTable tabla, String etiqueta, String valor, Font fuente) {
        tabla.addCell(celda(new Phrase(etiqueta, fuente), Element.ALIGN_LEFT));
        tabla.addCell(celda(new Phrase(valor, fuente), Element.ALIGN_RIGHT));
    }

    private static PdfPCell celda(Phrase contenido, int alineacion) {
        PdfPCell celda = new PdfPCell(contenido);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(1.5f);
        return celda;
    }

    private static void centrado(Document documento, String texto, Font fuente) throws DocumentException {
        Paragraph parrafo = new Paragraph(texto, fuente);
        parrafo.setAlignment(Element.ALIGN_CENTER);
        parrafo.setLeading(fuente.getSize() * 1.25f);
        documento.add(parrafo);
    }

    private static void texto(Document documento, String texto, Font fuente) throws DocumentException {
        Paragraph parrafo = new Paragraph(texto, fuente);
        parrafo.setLeading(fuente.getSize() * 1.3f);
        documento.add(parrafo);
    }

    /** Linea divisoria con aire arriba y abajo (una tabla de una celda con borde inferior). */
    private static void linea(Document documento) throws DocumentException {
        PdfPTable separador = new PdfPTable(1);
        separador.setWidthPercentage(100);
        separador.setSpacingBefore(3);
        separador.setSpacingAfter(4);
        PdfPCell celda = new PdfPCell();
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColor(Color.GRAY);
        celda.setBorderWidthBottom(0.5f);
        celda.setFixedHeight(2);
        separador.addCell(celda);
        documento.add(separador);
    }

    private static byte[] leerLogo() {
        ClassPathResource recurso = new ClassPathResource("documentos/logo.png");
        if (!recurso.exists()) {
            return null;
        }
        try (InputStream entrada = recurso.getInputStream()) {
            return entrada.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }
}
