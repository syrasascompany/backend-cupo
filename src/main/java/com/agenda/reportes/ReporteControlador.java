package com.agenda.reportes;

import com.agenda.reportes.ReporteServicio.Reporte;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReporteControlador {

    private final ReporteServicio servicio;

    /** GET /api/reportes?desde=2026-09-01&hasta=2026-09-30 */
    @GetMapping
    public Reporte reporte(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.generar(desde, hasta);
    }
}
