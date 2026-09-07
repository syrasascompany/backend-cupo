package com.agenda.espera;

import com.agenda.common.ContextoEmpresa;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/lista-espera")
@RequiredArgsConstructor
public class ListaEsperaControlador {

    private final ListaEsperaRepositorio repositorio;

    /**
     * Las que esperan y las que ya recibieron aviso. Antes solo salían las
     * que esperaban, así que al avisarles parecía que se borraban.
     */
    @GetMapping
    public List<ListaEspera> esperando() {
        Long empresaId = ContextoEmpresa.actual();
        List<ListaEspera> lista = new ArrayList<>(
                repositorio.findByEmpresaIdAndEstadoOrderByCreadoEnAsc(
                        empresaId, EstadoEspera.ESPERANDO));
        lista.addAll(repositorio.findByEmpresaIdAndEstadoOrderByCreadoEnAsc(
                empresaId, EstadoEspera.AVISADO));
        return lista;
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