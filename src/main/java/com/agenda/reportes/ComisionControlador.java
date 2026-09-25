package com.agenda.reportes;

import com.agenda.common.ContextoEmpresa;
import com.agenda.empresa.EmpresaRepositorio;
import com.agenda.reportes.ComisionServicio.Liquidacion;
import com.agenda.reportes.ComisionServicio.Movimiento;
import com.agenda.reportes.ComisionServicio.Reporte;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Las comisiones, en pantalla y en archivo.
 *
 * El archivo es un Excel de verdad, no un CSV: esto se lo entrega la
 * dueña al contador, y un texto separado por punto y coma no se ve como
 * algo que se pueda presentar.
 */
@RestController
@RequestMapping("/api/comisiones")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ComisionControlador {

    private final ComisionServicio servicio;
    private final EmpresaRepositorio empresas;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("hh:mm a");

    // Los colores de Cupo, para que el archivo se vea de la casa
    private static final byte[] CIRUELA = {(byte) 42, (byte) 10, (byte) 28};
    private static final byte[] FUCSIA = {(byte) 255, (byte) 31, (byte) 109};
    private static final byte[] ROSA_SUAVE = {(byte) 255, (byte) 235, (byte) 242};

    @GetMapping
    public Reporte comisiones(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.generar(desde, hasta);
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        Reporte r = servicio.generar(desde, hasta);
        String negocio = empresas.findById(ContextoEmpresa.actual())
                .map(e -> e.getNombre()).orElse("El salón");

        try (XSSFWorkbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            Estilos e = new Estilos(libro);
            hojaResumen(libro, e, r, negocio, desde, hasta);
            hojaDetalle(libro, e, r);

            libro.write(salida);

            String archivo = "Liquidacion " + FECHA.format(desde).replace('/', '-')
                    + " a " + FECHA.format(hasta).replace('/', '-') + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + archivo + "\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(salida.toByteArray());

        } catch (Exception ex) {
            throw new RuntimeException("No se pudo generar el archivo", ex);
        }
    }

    // ---------------- Hoja 1: el resumen ----------------

    private void hojaResumen(XSSFWorkbook libro, Estilos e, Reporte r,
                             String negocio, LocalDate desde, LocalDate hasta) {

        Sheet h = libro.createSheet("Resumen");
        h.setColumnWidth(0, 6500);
        h.setColumnWidth(1, 3600);
        h.setColumnWidth(2, 4200);
        h.setColumnWidth(3, 3000);
        h.setColumnWidth(4, 4200);
        h.setColumnWidth(5, 4200);

        int f = 0;

        // Encabezado
        celda(h, f, 0, negocio, e.titulo);
        h.addMergedRegion(new CellRangeAddress(f, f, 0, 5));
        h.getRow(f).setHeightInPoints(28);
        f++;

        celda(h, f, 0, "Liquidación del " + FECHA.format(desde)
                + " al " + FECHA.format(hasta), e.subtitulo);
        h.addMergedRegion(new CellRangeAddress(f, f, 0, 5));
        f += 2;

        // Los tres números que importan
        celda(h, f, 0, "Produjo el salón", e.etiquetaGrande);
        celda(h, f, 1, r.produccionTotal() / 100.0, e.plataGrande);
        h.addMergedRegion(new CellRangeAddress(f, f, 1, 2));
        f++;

        celda(h, f, 0, "Para las profesionales", e.etiquetaGrande);
        celda(h, f, 1, r.comisionesTotal() / 100.0, e.plataGrande);
        h.addMergedRegion(new CellRangeAddress(f, f, 1, 2));
        f++;

        celda(h, f, 0, "Queda al salón", e.etiquetaGrande);
        celda(h, f, 1, r.paraElSalon() / 100.0, e.plataGrandeVerde);
        h.addMergedRegion(new CellRangeAddress(f, f, 1, 2));
        f += 2;

        // Tabla por profesional
        celda(h, f, 0, "Cuánto se le paga a cada una", e.seccion);
        h.addMergedRegion(new CellRangeAddress(f, f, 0, 5));
        f++;

        String[] titulos = {"Profesional", "Citas", "Produjo",
                "Comisión", "Se le paga", "Queda al salón"};
        for (int c = 0; c < titulos.length; c++) celda(h, f, c, titulos[c], e.cabecera);
        h.getRow(f).setHeightInPoints(20);
        f++;

        boolean alterna = false;
        for (Liquidacion l : r.porProfesional()) {
            CellStyle texto = alterna ? e.textoAlt : e.texto;
            CellStyle plata = alterna ? e.plataAlt : e.plata;
            CellStyle pct = alterna ? e.porcentajeAlt : e.porcentaje;

            celda(h, f, 0, l.nombre(), texto);
            celda(h, f, 1, (double) l.citas(), alterna ? e.enteroAlt : e.entero);
            celda(h, f, 2, l.produccion() / 100.0, plata);
            celda(h, f, 3, l.comisionPct() / 100.0, pct);
            celda(h, f, 4, l.comision() / 100.0, alterna ? e.plataFucsiaAlt : e.plataFucsia);
            celda(h, f, 5, l.paraElSalon() / 100.0, plata);
            alterna = !alterna;
            f++;
        }

        celda(h, f, 0, "TOTAL", e.totalTexto);
        celda(h, f, 1, (double) r.citasCobradas(), e.totalEntero);
        celda(h, f, 2, r.produccionTotal() / 100.0, e.totalPlata);
        celda(h, f, 3, "", e.totalTexto);
        celda(h, f, 4, r.comisionesTotal() / 100.0, e.totalPlata);
        celda(h, f, 5, r.paraElSalon() / 100.0, e.totalPlata);
        f += 2;

        // Cómo pagaron
        celda(h, f, 0, "Cómo pagaron las clientas", e.seccion);
        h.addMergedRegion(new CellRangeAddress(f, f, 0, 5));
        f++;

        celda(h, f, 0, "Medio de pago", e.cabecera);
        celda(h, f, 1, "Total", e.cabecera);
        celda(h, f, 2, "", e.cabecera);
        f++;

        alterna = false;
        for (Object[] m : r.porMetodoPago()) {
            celda(h, f, 0, (String) m[0], alterna ? e.textoAlt : e.texto);
            celda(h, f, 1, ((Long) m[1]) / 100.0, alterna ? e.plataAlt : e.plata);
            celda(h, f, 2, "", alterna ? e.textoAlt : e.texto);
            alterna = !alterna;
            f++;
        }

        if (r.sinMetodoPago() > 0) {
            f++;
            celda(h, f, 0, "Ojo: " + r.sinMetodoPago()
                    + " citas quedaron sin marcar el medio de pago.", e.aviso);
            h.addMergedRegion(new CellRangeAddress(f, f, 0, 5));
        }
    }

    // ---------------- Hoja 2: el detalle ----------------

    private void hojaDetalle(XSSFWorkbook libro, Estilos e, Reporte r) {
        Sheet h = libro.createSheet("Detalle de citas");
        int[] anchos = {2800, 2600, 4200, 6500, 6000, 3600, 3800, 2600, 3800};
        for (int c = 0; c < anchos.length; c++) h.setColumnWidth(c, anchos[c]);

        int f = 0;
        celda(h, f, 0, "Cada cita atendida en el periodo", e.seccion);
        h.addMergedRegion(new CellRangeAddress(f, f, 0, 8));
        f++;

        String[] titulos = {"Fecha", "Hora", "Profesional", "Servicio", "Clienta",
                "Pagó con", "Valor", "Comisión", "Se le paga"};
        for (int c = 0; c < titulos.length; c++) celda(h, f, c, titulos[c], e.cabecera);
        h.getRow(f).setHeightInPoints(20);
        h.createFreezePane(0, f + 1);
        f++;

        boolean alterna = false;
        for (Movimiento m : r.movimientos()) {
            CellStyle texto = alterna ? e.textoAlt : e.texto;
            CellStyle plata = alterna ? e.plataAlt : e.plata;

            celda(h, f, 0, FECHA.format(m.fecha()), texto);
            celda(h, f, 1, HORA.format(m.hora()), texto);
            celda(h, f, 2, m.profesional(), texto);
            celda(h, f, 3, m.servicio(), texto);
            celda(h, f, 4, m.cliente(), texto);
            celda(h, f, 5, m.metodoPago(), texto);
            celda(h, f, 6, m.valor() / 100.0, plata);
            celda(h, f, 7, m.comisionPct() / 100.0, alterna ? e.porcentajeAlt : e.porcentaje);
            celda(h, f, 8, m.comision() / 100.0, alterna ? e.plataFucsiaAlt : e.plataFucsia);
            alterna = !alterna;
            f++;
        }

        if (r.movimientos().isEmpty()) {
            celda(h, f, 0, "No hubo citas atendidas en este periodo.", e.texto);
        }
    }

    // ---------------- Ayudas ----------------

    private void celda(Sheet h, int fila, int col, String valor, CellStyle estilo) {
        Row r = h.getRow(fila) != null ? h.getRow(fila) : h.createRow(fila);
        Cell c = r.createCell(col);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    private void celda(Sheet h, int fila, int col, double valor, CellStyle estilo) {
        Row r = h.getRow(fila) != null ? h.getRow(fila) : h.createRow(fila);
        Cell c = r.createCell(col);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    /**
     * Los estilos del libro.
     *
     * Van todos juntos porque Excel tiene un tope de estilos por archivo y
     * crearlos dentro de un bucle lo revienta con pocos cientos de filas.
     */
    private static class Estilos {
        final CellStyle titulo, subtitulo, seccion, cabecera;
        final CellStyle etiquetaGrande, plataGrande, plataGrandeVerde;
        final CellStyle texto, textoAlt, plata, plataAlt;
        final CellStyle plataFucsia, plataFucsiaAlt;
        final CellStyle porcentaje, porcentajeAlt, entero, enteroAlt;
        final CellStyle totalTexto, totalPlata, totalEntero, aviso;

        Estilos(XSSFWorkbook libro) {
            DataFormat formato = libro.createDataFormat();
            short moneda = formato.getFormat("\"$\"#,##0");
            short pct = formato.getFormat("0%");

            Font fTitulo = libro.createFont();
            fTitulo.setBold(true);
            fTitulo.setFontHeightInPoints((short) 17);
            fTitulo.setColor(IndexedColors.WHITE.getIndex());

            Font fBlanca = libro.createFont();
            fBlanca.setBold(true);
            fBlanca.setColor(IndexedColors.WHITE.getIndex());

            Font fNegrita = libro.createFont();
            fNegrita.setBold(true);

            Font fGrande = libro.createFont();
            fGrande.setBold(true);
            fGrande.setFontHeightInPoints((short) 14);

            Font fSuave = libro.createFont();
            fSuave.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

            titulo = fondo(libro, CIRUELA);
            titulo.setFont(fTitulo);
            titulo.setAlignment(HorizontalAlignment.LEFT);
            titulo.setVerticalAlignment(VerticalAlignment.CENTER);

            subtitulo = libro.createCellStyle();
            subtitulo.setFont(fSuave);

            seccion = libro.createCellStyle();
            seccion.setFont(fGrande);

            cabecera = fondo(libro, FUCSIA);
            cabecera.setFont(fBlanca);
            cabecera.setVerticalAlignment(VerticalAlignment.CENTER);
            borde(cabecera);

            etiquetaGrande = libro.createCellStyle();
            etiquetaGrande.setFont(fNegrita);

            plataGrande = libro.createCellStyle();
            plataGrande.setFont(fGrande);
            plataGrande.setDataFormat(moneda);

            plataGrandeVerde = libro.createCellStyle();
            Font fVerde = libro.createFont();
            fVerde.setBold(true);
            fVerde.setFontHeightInPoints((short) 14);
            fVerde.setColor(IndexedColors.GREEN.getIndex());
            plataGrandeVerde.setFont(fVerde);
            plataGrandeVerde.setDataFormat(moneda);

            texto = libro.createCellStyle();
            borde(texto);

            textoAlt = fondo(libro, ROSA_SUAVE);
            borde(textoAlt);

            plata = libro.createCellStyle();
            plata.setDataFormat(moneda);
            borde(plata);

            plataAlt = fondo(libro, ROSA_SUAVE);
            plataAlt.setDataFormat(moneda);
            borde(plataAlt);

            Font fFucsia = libro.createFont();
            fFucsia.setBold(true);
            fFucsia.setColor(IndexedColors.PINK.getIndex());

            plataFucsia = libro.createCellStyle();
            plataFucsia.setDataFormat(moneda);
            plataFucsia.setFont(fFucsia);
            borde(plataFucsia);

            plataFucsiaAlt = fondo(libro, ROSA_SUAVE);
            plataFucsiaAlt.setDataFormat(moneda);
            plataFucsiaAlt.setFont(fFucsia);
            borde(plataFucsiaAlt);

            porcentaje = libro.createCellStyle();
            porcentaje.setDataFormat(pct);
            porcentaje.setAlignment(HorizontalAlignment.CENTER);
            borde(porcentaje);

            porcentajeAlt = fondo(libro, ROSA_SUAVE);
            porcentajeAlt.setDataFormat(pct);
            porcentajeAlt.setAlignment(HorizontalAlignment.CENTER);
            borde(porcentajeAlt);

            entero = libro.createCellStyle();
            entero.setAlignment(HorizontalAlignment.CENTER);
            borde(entero);

            enteroAlt = fondo(libro, ROSA_SUAVE);
            enteroAlt.setAlignment(HorizontalAlignment.CENTER);
            borde(enteroAlt);

            totalTexto = fondo(libro, CIRUELA);
            totalTexto.setFont(fBlanca);
            borde(totalTexto);

            totalPlata = fondo(libro, CIRUELA);
            totalPlata.setFont(fBlanca);
            totalPlata.setDataFormat(moneda);
            borde(totalPlata);

            totalEntero = fondo(libro, CIRUELA);
            totalEntero.setFont(fBlanca);
            totalEntero.setAlignment(HorizontalAlignment.CENTER);
            borde(totalEntero);

            aviso = libro.createCellStyle();
            Font fAviso = libro.createFont();
            fAviso.setBold(true);
            fAviso.setColor(IndexedColors.DARK_YELLOW.getIndex());
            aviso.setFont(fAviso);
        }

        private CellStyle fondo(XSSFWorkbook libro, byte[] rgb) {
            CellStyle s = libro.createCellStyle();
            s.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(rgb, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return s;
        }

        private void borde(CellStyle s) {
            s.setBorderBottom(BorderStyle.THIN);
            s.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }
    }
}