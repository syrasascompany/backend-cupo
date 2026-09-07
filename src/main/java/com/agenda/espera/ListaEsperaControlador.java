package com.agenda.espera;

import com.agenda.common.ContextoEmpresa;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/lista-espera")
@RequiredArgsConstructor
public class ListaEsperaControlador {

    private final ListaEsperaRepositorio repositorio;

    @GetMapping
    public List<ListaEspera> esperando() {
        return repositorio.findByEmpresaIdAndEstadoOrderByCreadoEnAsc(
                ContextoEmpresa.actual(), EstadoEspera.ESPERANDO);
    }

    public record NuevaEspera(@NotNull Long servicioId, Long profesionalId,
                              @NotNull LocalDate desde, @NotNull LocalDate hasta,
                              @NotNull String telefono, String nombre) {}

    @PostMapping
    public ListaEspera agregar(@RequestBody NuevaEspera d) {
        ListaEspera e = new ListaEspera();
        e.setEmpresaId(ContextoEmpresa.actual());
        e.setServicioId(d.servicioId());
        e.setProfesionalId(d.profesionalId());
        e.setDesde(d.desde());
        e.setHasta(d.hasta());
        e.setTelefono(d.telefono());
        e.setNombre(d.nombre());
        return repositorio.save(e);
    }

    @DeleteMapping("/{id}")
    public void quitar(@PathVariable Long id) {
        repositorio.findById(id)
                .filter(e -> e.getEmpresaId().equals(ContextoEmpresa.actual()))
                .ifPresent(e -> e.setEstado(EstadoEspera.CANCELADO));
    }
}
