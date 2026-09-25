package com.agenda.reportes;

import com.agenda.reportes.ComisionServicio.Liquidacion;
import com.agenda.reportes.ComisionServicio.Movimiento;
import com.agenda.reportes.ComisionServicio.Reporte;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Las comisiones, en pantalla y en archivo.
 *
 * El archivo es el que la dueña le pasa al contador, así que lleva el
 * detalle de cada cita cobrada además del resumen por persona.
 */
@RestController
@RequestMapping("/api/comisiones")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ComisionControlador {

    private final ComisionServicio servicio;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("hh:mm a");

    @GetMapping
    public Reporte comisiones(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.generar(desde, hasta);
    }

    /**
     * Para abrir en Excel. Va como CSV con punto y coma, que es lo que
     * espera Excel en español, y con la marca BOM para que las tildes
     * no salgan rotas.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        Reporte r = servicio.generar(desde, hasta);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');

        sb.append("LIQUIDACIÓN DEL ").append(FECHA.format(desde))
                .append(" AL ").append(FECHA.format(hasta)).append('\n').append('\n');

        // ---- Resumen por persona: es lo primero que mira el contador ----
        sb.append(String.join(";",
                "Profesional", "Citas atendidas", "Produjo", "Comisión %",
                "Se le paga", "Queda al salón")).append('\n');

        for (Liquidacion l : r.porProfesional()) {
            sb.append(String.join(";",
                    campo(l.nombre()),
                    String.valueOf(l.citas()),
                    pesos(l.produccion()),
                    String.valueOf(l.comisionPct()).replace('.', ','),
                    pesos(l.comision()),
                    pesos(l.paraElSalon()))).append('\n');
        }

        sb.append(String.join(";", "TOTAL", String.valueOf(r.citasCobradas()),
                pesos(r.produccionTotal()), "",
                pesos(r.comisionesTotal()), pesos(r.paraElSalon()))).append('\n');

        // ---- Cómo pagaron ----
        sb.append('\n').append("CÓMO PAGARON").append('\n');
        for (Object[] m : r.porMetodoPago()) {
            sb.append(campo((String) m[0])).append(';').append(pesos((Long) m[1])).append('\n');
        }
        if (r.sinMetodoPago() > 0) {
            sb.append('\n')
                    .append("Ojo: ").append(r.sinMetodoPago())
                    .append(" citas quedaron sin marcar el método de pago.").append('\n');
        }

        // ---- Detalle cita por cita ----
        sb.append('\n').append("DETALLE").append('\n');
        sb.append(String.join(";",
                "Fecha", "Hora", "Profesional", "Servicio", "Clienta",
                "Pagó con", "Valor", "Comisión %", "Comisión")).append('\n');

        for (Movimiento m : r.movimientos()) {
            sb.append(String.join(";",
                    FECHA.format(m.fecha()),
                    HORA.format(m.hora()),
                    campo(m.profesional()),
                    campo(m.servicio()),
                    campo(m.cliente()),
                    campo(m.metodoPago()),
                    pesos(m.valor()),
                    String.valueOf(m.comisionPct()).replace('.', ','),
                    pesos(m.comision()))).append('\n');
        }

        byte[] datos = sb.toString().getBytes(StandardCharsets.UTF_8);
        String archivo = "liquidacion-" + desde + "-a-" + hasta + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + archivo + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(datos);
    }

    /** Los centavos pasan a pesos, y la coma decimal como la usa Excel aquí. */
    private String pesos(long centavos) {
        return String.valueOf(centavos / 100);
    }

    private String campo(String valor) {
        if (valor == null) return "";
        String limpio = valor.replace("\"", "\"\"").replace("\n", " ");
        return limpio.contains(";") || limpio.contains("\"") ? "\"" + limpio + "\"" : limpio;
    }
}