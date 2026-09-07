package com.agenda.agenda;

import com.agenda.agenda.HorarioServicio.Tramo;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/profesionales/{profesionalId}")
@RequiredArgsConstructor
public class HorarioControlador {

    private final HorarioServicio servicio;

    @GetMapping("/horarios")
    public List<HorarioBase> horarios(@PathVariable Long profesionalId) {
        return servicio.deProfesional(profesionalId);
    }

    /** Se manda la semana completa y reemplaza lo que había. */
    @PutMapping("/horarios")
    @PreAuthorize("hasRole('ADMIN')")
    public List<HorarioBase> reemplazar(@PathVariable Long profesionalId,
                                        @RequestBody List<Tramo> tramos) {
        return servicio.reemplazar(profesionalId, tramos);
    }

    @GetMapping("/excepciones")
    public List<ExcepcionHorario> excepciones(
            @PathVariable Long profesionalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.excepciones(profesionalId, desde, hasta);
    }

    public record NuevaExcepcion(LocalDate fecha, TipoExcepcion tipo,
                                 LocalTime horaInicio, LocalTime horaFin, String motivo) {}

    @PostMapping("/excepciones")
    @PreAuthorize("hasRole('ADMIN')")
    public ExcepcionHorario sellar(@PathVariable Long profesionalId,
                                   @RequestBody NuevaExcepcion d) {
        return servicio.sellar(profesionalId, d.fecha(), d.tipo(),
                d.horaInicio(), d.horaFin(), d.motivo());
    }

    @DeleteMapping("/excepciones/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void quitar(@PathVariable Long profesionalId, @PathVariable Long id) {
        servicio.quitarExcepcion(id);
    }
}
