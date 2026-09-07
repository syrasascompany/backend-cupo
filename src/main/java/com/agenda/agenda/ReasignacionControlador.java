package com.agenda.agenda;

import com.agenda.agenda.ReasignacionServicio.CitaAfectada;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reasignacion")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReasignacionControlador {

    private final ReasignacionServicio servicio;

    /** "Hoy no vino Ana: ¿qué hago con sus citas?" */
    @GetMapping
    public List<CitaAfectada> revisar(
            @RequestParam Long profesionalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return servicio.revisar(profesionalId, fecha);
    }

    public record Destino(@NotNull Long profesionalId, @NotNull LocalDateTime inicio) {}

    @PostMapping("/{citaId}")
    public Cita reasignar(@PathVariable Long citaId, @RequestBody Destino destino) {
        return servicio.reasignar(citaId, destino.profesionalId(), destino.inicio());
    }
}
