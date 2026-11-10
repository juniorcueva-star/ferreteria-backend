package com.ferreteria.service.documentos;

import com.ferreteria.entity.Empresa;
import com.ferreteria.entity.EscalaPrecio;
import com.ferreteria.entity.Presentacion;
import com.ferreteria.entity.Producto;
import com.ferreteria.entity.Ubicacion;
import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
import lombok.RequiredArgsConstructor;
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
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static com.ferreteria.service.documentos.MembretePdf.soles;

/**
 * Lista de precios para el cliente (pedido del dueno: mandarla por WhatsApp): membrete, productos activos agrupados
 * por categoria con su foto, codigo y el precio de cada presentacion; marca las ofertas vigentes (con el precio de
 * lista tachado) y el precio por mayor. Al pie, las cuentas bancarias.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListaPreciosPdf {

    private static final Color BORDE = new Color(212, 212, 212);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final MembretePdf membrete;
    private final Font tachado = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.STRIKETHRU, MembretePdf.GRIS);
    private final Font oferta = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, MembretePdf.NEGRO);

    /**
     * @param productos activos, ordenados por categoria y nombre
     * @param fotos     productoId -> foto JPEG o PNG (vacio = sin fotos)
     */
    public byte[] generar(Empresa empresa, Ubicacion tienda, List<Producto> productos, Map<Long, byte[]> fotos,
                          LocalDate hoy) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 30, 30, 30, 30);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.addTitle("Lista de precios " + FECHA.format(hoy));
            documento.open();
            membrete.encabezado(documento, empresa, tienda, "LISTA DE PRECIOS", FECHA.format(hoy));
            boolean conFotos = !fotos.isEmpty();
            Map<String, List<Producto>> porCategoria = new LinkedHashMap<>();
            for (Producto p : productos) {
                porCategoria.computeIfAbsent(p.getCategoria().getNombre(), c -> new ArrayList<>()).add(p);
            }
            PdfPTable tabla = new PdfPTable(conFotos ? new float[]{1.1f, 1.7f, 4.6f, 4.2f} : new float[]{1.7f, 5.2f, 4.4f});
            tabla.setWidthPercentage(100);
            tabla.setSpacingBefore(10);
            tabla.setHeaderRows(1);
            if (conFotos) {
                tabla.addCell(membrete.titulo("Foto"));
            }
            for (String columna : new String[]{"Código", "Producto", "Precio (con IGV)"}) {
                tabla.addCell(membrete.titulo(columna));
            }
            for (Map.Entry<String, List<Producto>> grupo : porCategoria.entrySet()) {
                PdfPCell categoria = new PdfPCell(new Phrase(grupo.getKey().toUpperCase(Locale.ROOT),
                        membrete.negrita));
                categoria.setColspan(conFotos ? 4 : 3);
                categoria.setBackgroundColor(MembretePdf.AMARILLO_CLARO);
                categoria.setBorderColor(MembretePdf.AMARILLO);
                categoria.setPadding(5);
                tabla.addCell(categoria);
                for (Producto p : grupo.getValue()) {
                    if (conFotos) {
                        tabla.addCell(foto(fotos.get(p.getId())));
                    }
                    tabla.addCell(membrete.celda(p.getCodigo(), Element.ALIGN_LEFT));
                    tabla.addCell(membrete.celda(p.getNombre(), Element.ALIGN_LEFT));
                    tabla.addCell(precios(p, hoy));
                }
            }
            documento.add(tabla);
            membrete.pie(documento, empresa, "Precios en soles con IGV incluido, válidos al " + FECHA.format(hoy)
                    + " y sujetos a cambio sin previo aviso. Las ofertas valen hasta la fecha indicada o hasta agotar "
                    + "stock.");
            documento.close();
        } catch (DocumentException e) {
            throw new ApiException(CodigoError.ERROR_INTERNO, "No se pudo generar la lista de precios");
        }
        return salida.toByteArray();
    }

    private PdfPCell foto(byte[] bytes) {
        PdfPCell celda = new PdfPCell();
        celda.setBorderColor(BORDE);
        celda.setPadding(3);
        celda.setFixedHeight(46);
        if (bytes != null) {
            try {
                Image imagen = Image.getInstance(bytes);
                imagen.scaleToFit(40, 40);
                celda.setImage(imagen);
            } catch (IOException | RuntimeException e) {
                log.debug("Foto omitida en la lista de precios: {}", e.getMessage());
            }
        }
        return celda;
    }

    /** "Unidad: S/ 0.50" por presentacion activa; la oferta vigente con la lista tachada; el precio por mayor. */
    private PdfPCell precios(Producto producto, LocalDate hoy) {
        Phrase texto = new Phrase();
        boolean primera = true;
        for (Presentacion pr : producto.getPresentaciones()) {
            if (!pr.isActivo()) {
                continue;
            }
            if (!primera) {
                texto.add(Chunk.NEWLINE);
            }
            primera = false;
            texto.add(new Chunk(pr.getNombre() + ": ", membrete.normal));
            if (pr.ofertaVigente(hoy)) {
                texto.add(new Chunk(soles(pr.getPrecioVenta()), tachado));
                texto.add(new Chunk(" " + soles(pr.getPrecioOferta()) + " OFERTA", oferta));
                texto.add(new Chunk(" hasta el " + FECHA.format(pr.getOfertaHasta()), membrete.pequena));
            } else {
                texto.add(new Chunk(soles(pr.getPrecioVenta()), membrete.negrita));
            }
            if (!pr.getEscalas().isEmpty()) {
                texto.add(new Chunk("\nPor mayor: " + escalas(pr.getEscalas(), e -> soles(e.getPrecio())),
                        membrete.pequena));
            }
        }
        PdfPCell celda = new PdfPCell(texto);
        celda.setBorderColor(BORDE);
        celda.setPadding(4);
        return celda;
    }

    private static String escalas(List<EscalaPrecio> escalas, Function<EscalaPrecio, String> precio) {
        return String.join(" · ", escalas.stream()
                .map(e -> "desde " + e.getCantidadMinima().stripTrailingZeros().toPlainString() + " a " + precio.apply(e))
                .toList());
    }
}
