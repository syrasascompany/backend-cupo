package com.agenda.catalogo;

import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogoControlador {

    private final CatalogoServicio servicio;

    // ---------- Profesionales ----------

    public record DatosProfesional(@NotBlank String nombre, String telefono,
                                   String color, Boolean activo) {}

    @GetMapping("/profesionales")
    public List<Profesional> profesionales(
            @RequestParam(defaultValue = "true") boolean soloActivos) {
        return servicio.listarProfesionales(soloActivos);
    }

    @PostMapping("/profesionales")
    @PreAuthorize("hasRole('ADMIN')")
    public Profesional crearProfesional(@RequestBody DatosProfesional d) {
        return servicio.crearProfesional(d.nombre(), d.telefono(), d.color());
    }

    @PutMapping("/profesionales/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Profesional actualizarProfesional(@PathVariable Long id,
                                             @RequestBody DatosProfesional d) {
        return servicio.actualizarProfesional(id, d.nombre(), d.telefono(), d.color(), d.activo());
    }

    /**
     * Retirar a una profesional que se va del salón.
     * Si le quedan citas agendadas, el sistema no deja hasta reasignarlas,
     * salvo que se mande forzar=true a propósito.
     */
    @DeleteMapping("/profesionales/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void retirarProfesional(@PathVariable Long id,
                                   @RequestParam(defaultValue = "false") boolean forzar) {
        servicio.desactivarProfesional(id, forzar);
    }

    @GetMapping("/profesionales/{id}/citas-pendientes")
    public long citasPendientes(@PathVariable Long id) {
        return servicio.citasFuturas(id);
    }

    /** Define de una vez todos los servicios que hace una profesional. */
    @PutMapping("/profesionales/{id}/servicios")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ServicioProfesional> definirServicios(
            @PathVariable Long id,
            @RequestBody List<CatalogoServicio.AsignacionServicio> lista) {
        return servicio.definirServiciosDe(id, lista);
    }

    // ---------- Servicios ----------

    public record DatosServicio(@NotBlank String nombre, Long precioCentavos,
                                Integer duracionMin, Boolean activo) {}

    @GetMapping("/servicios")
    public List<Servicio> servicios(@RequestParam(defaultValue = "true") boolean soloActivos) {
        return servicio.listarServicios(soloActivos);
    }

    @PostMapping("/servicios")
    @PreAuthorize("hasRole('ADMIN')")
    public Servicio crearServicio(@RequestBody DatosServicio d) {
        return servicio.crearServicio(d.nombre(), d.precioCentavos(), d.duracionMin());
    }

    @PutMapping("/servicios/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Servicio actualizarServicio(@PathVariable Long id, @RequestBody DatosServicio d) {
        return servicio.actualizarServicio(id, d.nombre(), d.precioCentavos(),
                d.duracionMin(), d.activo());
    }

    @DeleteMapping("/servicios/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void desactivarServicio(@PathVariable Long id) {
        servicio.desactivarServicio(id);
    }

    // ---------- Asignación con duración propia ----------

    public record Asignacion(@NotNull Long profesionalId, Integer duracionMin) {}

    @GetMapping("/servicios/{id}/profesionales")
    public List<ServicioProfesional> quienLoHace(@PathVariable Long id) {
        return servicio.duracionesDeServicio(id);
    }

    @GetMapping("/profesionales/{id}/servicios")
    public List<ServicioProfesional> queHace(@PathVariable Long id) {
        return servicio.duracionesDeProfesional(id);
    }

    @PostMapping("/servicios/{id}/profesionales")
    @PreAuthorize("hasRole('ADMIN')")
    public ServicioProfesional asignar(@PathVariable Long id, @RequestBody Asignacion a) {
        return servicio.asignar(id, a.profesionalId(), a.duracionMin());
    }

    @DeleteMapping("/servicios/{id}/profesionales/{profesionalId}")
    @PreAuthorize("hasRole('ADMIN')")
    public void quitar(@PathVariable Long id, @PathVariable Long profesionalId) {
        servicio.quitarAsignacion(id, profesionalId);
    }
}
