package com.agenda.agenda;

import com.agenda.security.UsuarioAutenticado;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/citas")
@RequiredArgsConstructor
public class CitaControlador {

    private final CitaServicio servicio;

    public record NuevaCita(@NotNull Long servicioId, @NotNull Long profesionalId,
                            @NotNull LocalDateTime inicio,
                            String nombreCliente, String telefonoCliente, String notas) {}

    public record Reprogramacion(@NotNull Long profesionalId, @NotNull LocalDateTime inicio) {}

    /**
     * La agenda del día. Si quien pregunta es una trabajadora, se le
     * devuelven solo sus citas, sin importar lo que pida.
     */
    @GetMapping
    public List<CitaVista> delDia(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                  @RequestParam(required = false) Long profesionalId,
                                  @AuthenticationPrincipal UsuarioAutenticado usuario) {
        Long filtro = usuario.esTrabajadora() ? usuario.profesionalId() : profesionalId;
        return servicio.vistaDelDia(fecha, filtro);
    }

    @PostMapping
    public Cita crear(@RequestBody NuevaCita datos) {
        return servicio.crear(datos.servicioId(), datos.profesionalId(), datos.inicio(),
                datos.nombreCliente(), datos.telefonoCliente(), OrigenCita.PANEL, datos.notas());
    }

    @PatchMapping("/{id}/reprogramar")
    public Cita reprogramar(@PathVariable Long id, @RequestBody Reprogramacion datos) {
        return servicio.reprogramar(id, datos.profesionalId(), datos.inicio());
    }

    /**
     * El botón "finalizado" de la trabajadora entra por aquí.
     *
     * El método de pago es opcional: si la clienta paga en administración,
     * lo marca el dueño después con el endpoint de abajo.
     */
    @PatchMapping("/{id}/estado")
    public void cambiarEstado(@PathVariable Long id, @RequestParam EstadoCita valor,
                              @RequestParam(required = false) MetodoPago metodoPago,
                              @AuthenticationPrincipal UsuarioAutenticado usuario) {
        servicio.cambiarEstado(id, valor,
                usuario.esTrabajadora() ? usuario.profesionalId() : null,
                metodoPago);
    }

    /**
     * El administrador marca o corrige con qué pagó una cita.
     *
     * Con valorCentavos se puede registrar lo que de verdad se cobró
     * cuando hubo descuento o se cobró distinto al precio de lista.
     */
    @PatchMapping("/{id}/pago")
    @PreAuthorize("hasRole('ADMIN')")
    public Cita marcarPago(@PathVariable Long id,
                           @RequestParam MetodoPago metodo,
                           @RequestParam(required = false) Long valorCentavos) {
        return servicio.marcarPago(id, metodo, valorCentavos);
    }
}