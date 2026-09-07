package com.agenda.whatsapp;

import com.agenda.empresa.Empresa;
import com.agenda.empresa.EmpresaRepositorio;
import com.agenda.common.NoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Sirve para probar el bot sin Meta y sin número real.
 *
 * Solo existe cuando app.whatsapp.simular=true. En producción esta clase
 * ni siquiera se carga, así que no hay forma de dejarla abierta por error.
 */
@RestController
@RequestMapping("/api/pruebas/bot")
@ConditionalOnProperty(name = "app.whatsapp.simular", havingValue = "true")
@RequiredArgsConstructor
public class PruebaBotControlador {

    private final EmpresaRepositorio empresas;
    private final BotServicio bot;
    private final WhatsappCliente wa;

    /**
     * Manda un mensaje como si lo escribiera una clienta.
     *
     * - "texto" para escribir libre
     * - "seleccion" para tocar una opción de la lista (el id entre corchetes)
     */
    public record Mensaje(String empresaSlug, String telefono,
                          String texto, String seleccion, String nombre) {}

    @PostMapping
    public Map<String, Object> escribir(@RequestBody Mensaje m) {
        Empresa empresa = empresas.findBySlug(
                m.empresaSlug() == null ? "tony-nails" : m.empresaSlug())
                .orElseThrow(() -> new NoEncontradoException("Empresa no encontrada"));

        String telefono = m.telefono() == null ? "573001112233" : m.telefono();

        wa.limpiarBandeja(telefono);

        bot.procesar(empresa, new BotServicio.Entrante(
                telefono,
                m.nombre() == null ? "Clienta de prueba" : m.nombre(),
                m.texto(),
                m.seleccion()));

        List<String> respuestas = wa.bandejaDe(telefono);

        return Map.of(
                "telefono", telefono,
                "respondio", respuestas.size(),
                "mensajes", respuestas);
    }

    /**
     * Simula que el dueño le escribió a la clienta desde su celular.
     * Después de esto, el bot debe quedarse callado con ese número.
     */
    @PostMapping("/eco")
    public Map<String, String> eco(@RequestBody Mensaje m) {
        Empresa empresa = empresas.findBySlug(
                m.empresaSlug() == null ? "tony-nails" : m.empresaSlug())
                .orElseThrow(() -> new NoEncontradoException("Empresa no encontrada"));

        String telefono = m.telefono() == null ? "573001112233" : m.telefono();
        bot.pausarPorHumano(empresa, telefono);

        return Map.of("mensaje",
                "Bot pausado 12 horas con " + telefono + ". Escríbale y no debe responder.");
    }

    /** Borra la conversación para empezar la prueba de cero. */
    @DeleteMapping("/{telefono}")
    public Map<String, String> reiniciar(@PathVariable String telefono) {
        wa.limpiarBandeja(telefono);
        return Map.of("mensaje", "Bandeja limpia. La conversación sigue donde iba.");
    }
}
