package com.agenda.whatsapp;

import com.agenda.empresa.Empresa;
import com.agenda.empresa.EmpresaRepositorio;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhook/whatsapp")
@Slf4j
@RequiredArgsConstructor
public class WebhookControlador {

    private final EmpresaRepositorio empresas;
    private final MensajeProcesadoRepositorio procesados;
    private final BotServicio bot;

    @Value("${app.whatsapp.verify-token}")
    private String tokenVerificacion;

    /** Meta llama esto una vez, al configurar el webhook. */
    @GetMapping
    public ResponseEntity<String> verificar(
            @RequestParam("hub.mode") String modo,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String reto) {

        if ("subscribe".equals(modo) && tokenVerificacion.equals(token)) {
            return ResponseEntity.ok(reto);
        }
        return ResponseEntity.status(403).body("Token de verificación inválido");
    }

    /**
     * Entrada de mensajes.
     *
     * Siempre se responde 200, incluso si algo falla: si Meta recibe un
     * error, reintenta el mismo mensaje una y otra vez.
     */
    @PostMapping
    public ResponseEntity<Void> recibir(@RequestBody JsonNode cuerpo) {
        try {
            for (JsonNode entrada : cuerpo.path("entry")) {
                for (JsonNode cambio : entrada.path("changes")) {
                    procesarCambio(cambio.path("value"));
                }
            }
        } catch (Exception e) {
            log.error("Error procesando el webhook de WhatsApp", e);
        }
        return ResponseEntity.ok().build();
    }

    private void procesarCambio(JsonNode valor) {
        String phoneNumberId = valor.path("metadata").path("phone_number_id").asText(null);
        if (phoneNumberId == null) return;

        Empresa empresa = empresas.findByWaPhoneNumberId(phoneNumberId).orElse(null);
        if (empresa == null) {
            log.warn("Llegó un mensaje de un número no registrado: {}", phoneNumberId);
            return;
        }
        if (!Boolean.TRUE.equals(empresa.getActiva())) return;

        // Coexistence: lo que el dueño escribe desde su celular llega aquí.
        // No es una clienta escribiendo, es una persona atendiendo, así que
        // el bot se hace a un lado en esa conversación.
        for (JsonNode eco : valor.path("message_echoes")) {
            String para = eco.path("to").asText(null);
            if (para != null) bot.pausarPorHumano(empresa, para);
        }

        for (JsonNode mensaje : valor.path("messages")) {
            String idMensaje = mensaje.path("id").asText();

            // Meta reintenta: si ya se procesó, se ignora.
            if (idMensaje.isBlank() || procesados.existsById(idMensaje)) continue;
            procesados.save(new MensajeProcesado(idMensaje, java.time.Instant.now()));

            String telefono = mensaje.path("from").asText();
            String nombrePerfil = valor.path("contacts").path(0)
                    .path("profile").path("name").asText(null);

            String texto = null;
            String seleccion = null;

            switch (mensaje.path("type").asText()) {
                case "text" -> texto = mensaje.path("text").path("body").asText();
                case "interactive" -> {
                    JsonNode interactivo = mensaje.path("interactive");
                    if (interactivo.has("list_reply")) {
                        seleccion = interactivo.path("list_reply").path("id").asText();
                    } else if (interactivo.has("button_reply")) {
                        seleccion = interactivo.path("button_reply").path("id").asText();
                    }
                }
                case "button" -> seleccion = mensaje.path("button").path("payload").asText();
                default -> texto = "";     // audio, imagen, etc.: se responde con el menú
            }

            bot.procesar(empresa, new BotServicio.Entrante(
                    telefono, nombrePerfil, texto, seleccion));
        }
    }
}
