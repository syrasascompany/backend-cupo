package com.agenda.whatsapp;

import com.agenda.common.ContextoEmpresa;
import com.agenda.common.NoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Con Coexistence, el dueño contesta desde su celular y el bot se hace a
 * un lado. Esta pantalla le muestra en cuáles conversaciones pasó eso,
 * para que no se le quede alguna sin responder.
 */
@RestController
@RequestMapping("/api/conversaciones")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ConversacionControlador {

    private final ConversacionRepositorio conversaciones;

    public record Atendida(Long id, String telefono, String nombreCliente,
                           Instant ultimoMensaje, Instant pausadoHasta) {}

    @GetMapping("/atendidas")
    public List<Atendida> atendidas() {
        return conversaciones
                .findByEmpresaIdAndPausadoHastaAfterOrderByUltimoMensajeDesc(
                        ContextoEmpresa.actual(), Instant.now())
                .stream()
                .map(c -> new Atendida(c.getId(), c.getTelefono(), c.getNombreCliente(),
                        c.getUltimoMensaje(), c.getPausadoHasta()))
                .toList();
    }

    /** Devolverle la conversación al bot antes de que se cumpla la pausa. */
    @PostMapping("/{id}/devolver-al-bot")
    public Map<String, String> devolver(@PathVariable Long id) {
        Conversacion c = conversaciones.findById(id)
                .filter(x -> x.getEmpresaId().equals(ContextoEmpresa.actual()))
                .orElseThrow(() -> new NoEncontradoException("Conversación no encontrada"));

        c.setPausadoHasta(null);
        c.reiniciar();
        conversaciones.save(c);

        return Map.of("mensaje", "El bot vuelve a atender esta conversación.");
    }
}
