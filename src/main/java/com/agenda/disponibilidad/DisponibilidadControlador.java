package com.agenda.disponibilidad;

import com.agenda.disponibilidad.MotorDisponibilidad.Cupo;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/disponibilidad")
@RequiredArgsConstructor
public class DisponibilidadControlador {

    private final DisponibilidadServicio servicio;

    /** GET /api/disponibilidad?servicioId=3&fecha=2026-09-08[&profesionalId=5] */
    @GetMapping
    public List<Cupo> cupos(@RequestParam Long servicioId,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                            @RequestParam(required = false) Long profesionalId) {
        return servicio.cupos(servicioId, fecha, profesionalId);
    }
}
