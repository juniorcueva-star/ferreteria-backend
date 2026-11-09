package com.ferreteria.service.documentos;

import com.ferreteria.entity.Pago;
import com.ferreteria.entity.Venta;
import com.ferreteria.entity.VentaDetalle;
import com.ferreteria.entity.enums.CondicionVenta;
import com.ferreteria.entity.enums.EstadoPago;
import com.ferreteria.entity.enums.EstadoVenta;
import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
import lombok.RequiredArgsConstructor;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.ferreteria.service.documentos.MembretePdf.soles;

/**
 * PDF de la nota de venta con el membrete del negocio: cliente, condicion, detalle, totales, pagos y, si es al
 * credito, el saldo pendiente. No es una boleta electronica (aun no se envia a SUNAT).
 */
@Component
@RequiredArgsConstructor
public class VentaPdf {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final MembretePdf membrete;
    private final Font anulada = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(220, 38, 38));

    public byte[] generar(Venta venta) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.addTitle("Nota de venta " + venta.getNumeroDocumento());
            documento.open();
            membrete.encabezado(documento, venta.getEmpresa(), venta.getUbicacion(), "NOTA DE VENTA",
                    venta.getNumeroDocumento());
            if (venta.getEstado() == EstadoVenta.ANULADA) {
                Paragraph marca = new Paragraph("ANULADA" + (venta.getMotivoAnulacion() == null ? ""
                        : ": " + venta.getMotivoAnulacion()), anulada);
                marca.setSpacingBefore(6);
                documento.add(marca);
            }
            datos(documento, venta);
            detalle(documento, venta);
            totales(documento, venta);
            pagos(documento, venta);
            membrete.pie(documento, venta.getEmpresa(), "Precios en soles e incluyen IGV. "
                    + "Nota de venta interna: no reemplaza a la boleta o factura electrónica.");
            documento.close();
        } catch (DocumentException e) {
            throw new ApiException(CodigoError.ERROR_INTERNO, "No se pudo generar el PDF de la nota de venta");
        }
        return salida.toByteArray();
    }

    private void datos(Document documento, Venta v) throws DocumentException {
        List<String[]> pares = new ArrayList<>();
        pares.add(new String[]{"Fecha:", FECHA_HORA.format(v.getFecha().atZoneSameInstant(LIMA))});
        pares.add(new String[]{"Condición:", v.getCondicion() == CondicionVenta.CONTADO ? "Contado" : "Crédito"});
        if (v.getCliente() == null) {
            pares.add(new String[]{"Cliente:", "Cliente varios"});
        } else {
            pares.add(new String[]{"Cliente:", v.getCliente().getNombre()});
            if (v.getCliente().getNumeroDocumento() != null) {
                pares.add(new String[]{v.getCliente().getTipoDocumento() + ":", v.getCliente().getNumeroDocumento()});
            }
        }
        if (v.getFechaVencimiento() != null) {
            pares.add(new String[]{"Vence:", FECHA.format(v.getFechaVencimiento())});
        }
        pares.add(new String[]{"Vendedor:", v.getUsuario().getNombres()});
        membrete.datos(documento, pares);
    }

    private void detalle(Document documento, Venta v) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[]{1.2f, 6, 1.8f, 1.6f, 2});
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(10);
        tabla.setHeaderRows(1);
        for (String columna : new String[]{"Cant.", "Descripción", "P. unit.", "Desc.", "Importe"}) {
            tabla.addCell(membrete.titulo(columna));
        }
        for (VentaDetalle d : v.getDetalles()) {
            tabla.addCell(membrete.celda(d.getCantidad().stripTrailingZeros().toPlainString(), Element.ALIGN_RIGHT));
            tabla.addCell(membrete.celda(d.getDescripcion(), Element.ALIGN_LEFT));
            tabla.addCell(membrete.celda(soles(d.getPrecioUnitario()), Element.ALIGN_RIGHT));
            tabla.addCell(membrete.celda(d.getDescuento().signum() == 0 ? "" : soles(d.getDescuento()),
                    Element.ALIGN_RIGHT));
            tabla.addCell(membrete.celda(soles(d.getSubtotal()), Element.ALIGN_RIGHT));
        }
        documento.add(tabla);
    }

    private void totales(Document documento, Venta v) throws DocumentException {
        List<String[]> filas = new ArrayList<>();
        filas.add(new String[]{"Op. gravada", soles(v.getSubtotal())});
        filas.add(new String[]{"IGV (18 %)", soles(v.getIgv())});
        if (v.getDescuento().signum() > 0) {
            filas.add(new String[]{"Descuentos aplicados", soles(v.getDescuento())});
        }
        filas.add(new String[]{"TOTAL", soles(v.getTotal())});
        membrete.totales(documento, filas);
    }

    private void pagos(Document documento, Venta v) throws DocumentException {
        List<Pago> validos = v.getPagos().stream().filter(p -> p.getEstado() == EstadoPago.VALIDO).toList();
        if (validos.isEmpty() && v.getSaldoPendiente().signum() == 0) {
            return;
        }
        PdfPTable tabla = new PdfPTable(new float[]{3, 3, 2});
        tabla.setWidthPercentage(60);
        tabla.setHorizontalAlignment(Element.ALIGN_LEFT);
        tabla.setSpacingBefore(10);
        for (String columna : new String[]{"Pago", "Fecha", "Monto"}) {
            tabla.addCell(membrete.titulo(columna));
        }
        for (Pago p : validos) {
            String metodo = p.getMetodoPago().getNombre()
                    + (p.getNumeroOperacion() == null ? "" : " (Op. " + p.getNumeroOperacion() + ")");
            tabla.addCell(membrete.celda(metodo, Element.ALIGN_LEFT));
            tabla.addCell(membrete.celda(FECHA_HORA.format(p.getFecha().atZoneSameInstant(LIMA)), Element.ALIGN_LEFT));
            tabla.addCell(membrete.celda(soles(p.getMonto()), Element.ALIGN_RIGHT));
        }
        if (v.getCondicion() == CondicionVenta.CREDITO) {
            tabla.addCell(membrete.celda("Saldo pendiente", Element.ALIGN_LEFT));
            tabla.addCell(membrete.celda("", Element.ALIGN_LEFT));
            tabla.addCell(membrete.celda(soles(v.getSaldoPendiente()), Element.ALIGN_RIGHT));
        }
        documento.add(tabla);
    }
}
