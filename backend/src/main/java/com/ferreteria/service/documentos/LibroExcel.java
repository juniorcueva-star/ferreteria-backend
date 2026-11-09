package com.ferreteria.service.documentos;

import com.ferreteria.exception.ApiException;
import com.ferreteria.exception.CodigoError;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Libro de Excel (.xlsx) con el estilo del negocio: titulo, subtitulo, fila de titulos en amarillo, filtros,
 * primera fila fija y formatos de soles, cantidades y fechas. Cada hoja se arma con {@link #hoja}.
 */
public final class LibroExcel implements AutoCloseable {

    /** Tipo de dato de una columna: define el formato de la celda. */
    public enum Tipo { TEXTO, SOLES, CANTIDAD, ENTERO, FECHA, FECHA_HORA }

    public record Columna(String titulo, Tipo tipo, int ancho) {
    }

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final XSSFWorkbook libro = new XSSFWorkbook();
    private final CellStyle titulo;
    private final CellStyle subtitulo;
    private final CellStyle encabezado;
    private final CellStyle texto;
    private final CellStyle soles;
    private final CellStyle cantidad;
    private final CellStyle entero;

    public LibroExcel() {
        Font grande = libro.createFont();
        grande.setBold(true);
        grande.setFontHeightInPoints((short) 14);
        titulo = libro.createCellStyle();
        titulo.setFont(grande);

        Font gris = libro.createFont();
        gris.setItalic(true);
        gris.setColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_50_PERCENT.getIndex());
        subtitulo = libro.createCellStyle();
        subtitulo.setFont(gris);

        Font negrita = libro.createFont();
        negrita.setBold(true);
        XSSFCellStyle amarillo = libro.createCellStyle();
        amarillo.setFont(negrita);
        amarillo.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 245, (byte) 196, 0}, null));
        amarillo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        amarillo.setBorderBottom(BorderStyle.THIN);
        encabezado = amarillo;

        texto = libro.createCellStyle();
        soles = libro.createCellStyle();
        soles.setDataFormat(libro.createDataFormat().getFormat("\"S/\" #,##0.00"));
        cantidad = libro.createCellStyle();
        cantidad.setDataFormat(libro.createDataFormat().getFormat("#,##0.###"));
        entero = libro.createCellStyle();
        entero.setDataFormat(libro.createDataFormat().getFormat("#,##0"));
    }

    /**
     * Agrega una hoja con titulo, subtitulo (Ej: el rango de fechas), columnas y filas.
     * Cada fila es una lista de valores en el mismo orden que las columnas (null = celda vacia).
     */
    public void hoja(String nombre, String tituloHoja, String subtituloHoja, List<Columna> columnas,
                     List<List<Object>> filas) {
        Sheet hoja = libro.createSheet(nombre);
        hoja.createRow(0).createCell(0).setCellValue(tituloHoja);
        hoja.getRow(0).getCell(0).setCellStyle(titulo);
        hoja.createRow(1).createCell(0).setCellValue(subtituloHoja);
        hoja.getRow(1).getCell(0).setCellStyle(subtitulo);

        int filaTitulos = 3;
        Row titulos = hoja.createRow(filaTitulos);
        for (int i = 0; i < columnas.size(); i++) {
            Cell celda = titulos.createCell(i);
            celda.setCellValue(columnas.get(i).titulo());
            celda.setCellStyle(encabezado);
            hoja.setColumnWidth(i, columnas.get(i).ancho() * 256);
        }
        for (int f = 0; f < filas.size(); f++) {
            Row fila = hoja.createRow(filaTitulos + 1 + f);
            List<Object> valores = filas.get(f);
            for (int c = 0; c < columnas.size(); c++) {
                escribir(fila.createCell(c), columnas.get(c).tipo(), c < valores.size() ? valores.get(c) : null);
            }
        }
        if (filas.isEmpty()) {
            hoja.createRow(filaTitulos + 1).createCell(0).setCellValue("Sin datos para los filtros elegidos");
        } else {
            hoja.setAutoFilter(new CellRangeAddress(filaTitulos, filaTitulos + filas.size(), 0, columnas.size() - 1));
        }
        hoja.createFreezePane(0, filaTitulos + 1);
    }

    public byte[] aBytes() {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new ApiException(CodigoError.ERROR_INTERNO, "No se pudo generar el archivo de Excel");
        }
    }

    @Override
    public void close() {
        try {
            libro.close();
        } catch (IOException e) {
            // El libro esta en memoria: no hay recurso externo que cerrar
        }
    }

    private void escribir(Cell celda, Tipo tipo, Object valor) {
        if (valor == null) {
            celda.setCellStyle(texto);
            return;
        }
        switch (tipo) {
            case SOLES -> numero(celda, valor, soles);
            case CANTIDAD -> numero(celda, valor, cantidad);
            case ENTERO -> numero(celda, valor, entero);
            case FECHA -> celda.setCellValue(valor instanceof LocalDate f ? FECHA.format(f) : valor.toString());
            case FECHA_HORA -> celda.setCellValue(valor instanceof OffsetDateTime f
                    ? FECHA_HORA.format(f.atZoneSameInstant(LIMA)) : valor.toString());
            case TEXTO -> celda.setCellValue(valor.toString());
        }
    }

    private static void numero(Cell celda, Object valor, CellStyle estilo) {
        double numero = valor instanceof BigDecimal b ? b.doubleValue() : ((Number) valor).doubleValue();
        celda.setCellValue(numero);
        celda.setCellStyle(estilo);
    }
}
