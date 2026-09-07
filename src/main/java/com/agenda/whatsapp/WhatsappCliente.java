package com.agenda.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Envío de mensajes por la Cloud API de Meta.
 *
 * Se va DIRECTO contra Meta, sin proveedor intermedio. En Colombia el
 * mensaje de utilidad cuesta centavos; los recargos de un BSP se comen
 * el margen.
 */
@Component
@Slf4j
public class WhatsappCliente {

    private final RestClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String version;

    /**
     * En simulación no se llama a Meta: los mensajes se guardan en memoria
     * para poder probar el bot completo sin conexión ni número real.
     */
    private final boolean simular;
    private final Map<String, List<String>> bandeja = new ConcurrentHashMap<>();

    public WhatsappCliente(@Value("${app.whatsapp.version:v21.0}") String version,
                           @Value("${app.whatsapp.simular:false}") boolean simular) {
        this.version = version;
        this.simular = simular;
        this.http = RestClient.builder().baseUrl("https://graph.facebook.com").build();
    }

    public boolean enSimulacion() { return simular; }

    /** Lo que el bot le "envió" a ese número, para las pruebas. */
    public List<String> bandejaDe(String telefono) {
        return bandeja.getOrDefault(telefono, List.of());
    }

    public void limpiarBandeja(String telefono) {
        bandeja.remove(telefono);
    }

    private void guardar(String para, String texto) {
        bandeja.computeIfAbsent(para, k -> Collections.synchronizedList(new ArrayList<>()))
               .add(texto);
        log.info("[WhatsApp simulado] a {} -> {}", para, texto);
    }

    private void enviar(String phoneNumberId, String token, Map<String, Object> cuerpo) {
        if (simular) return;   // en simulación ya se guardó arriba

        try {
            http.post()
                .uri("/{version}/{id}/messages", version, phoneNumberId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.writeValueAsString(cuerpo))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            // Un mensaje que no sale nunca debe tumbar la agenda.
            log.error("Falló el envío por WhatsApp: {}", e.getMessage());
        }
    }

    /** Texto libre. Solo válido dentro de las 24 horas desde el último mensaje del cliente. */
    public void texto(String phoneNumberId, String token, String para, String texto) {
        if (simular) { guardar(para, texto); return; }
        enviar(phoneNumberId, token, Map.of(
                "messaging_product", "whatsapp",
                "to", para,
                "type", "text",
                "text", Map.of("body", texto, "preview_url", false)));
    }

    public record Opcion(String id, String titulo, String descripcion) {}

    /**
     * Lista de opciones. Sirve hasta 10 filas y es lo que evita que el bot
     * tenga que entender frases sueltas.
     */
    public void lista(String phoneNumberId, String token, String para,
                      String cuerpo, String textoBoton, String tituloSeccion,
                      List<Opcion> opciones) {

        List<Map<String, Object>> filas = opciones.stream().limit(10)
                .map(o -> {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("id", o.id());
                    fila.put("title", recortar(o.titulo(), 24));
                    if (o.descripcion() != null) fila.put("description", recortar(o.descripcion(), 72));
                    return fila;
                }).toList();

        if (simular) {
            StringBuilder sb = new StringBuilder(cuerpo).append("\n");
            opciones.forEach(o -> sb.append("  [").append(o.id()).append("] ")
                    .append(o.titulo())
                    .append(o.descripcion() == null ? "" : " — " + o.descripcion())
                    .append("\n"));
            guardar(para, sb.toString().trim());
            return;
        }
        enviar(phoneNumberId, token, Map.of(
                "messaging_product", "whatsapp",
                "to", para,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "list",
                        "body", Map.of("text", cuerpo),
                        "action", Map.of(
                                "button", recortar(textoBoton, 20),
                                "sections", List.of(Map.of(
                                        "title", recortar(tituloSeccion, 24),
                                        "rows", filas))))));
    }

    /** Hasta tres botones. Para confirmar, cancelar o reprogramar. */
    public void botones(String phoneNumberId, String token, String para,
                        String cuerpo, List<Opcion> opciones) {

        List<Map<String, Object>> botones = opciones.stream().limit(3)
                .map(o -> Map.<String, Object>of(
                        "type", "reply",
                        "reply", Map.of("id", o.id(), "title", recortar(o.titulo(), 20))))
                .toList();

        if (simular) {
            StringBuilder sb = new StringBuilder(cuerpo).append("\n");
            opciones.forEach(o -> sb.append("  [").append(o.id()).append("] ")
                    .append(o.titulo()).append("\n"));
            guardar(para, sb.toString().trim());
            return;
        }
        enviar(phoneNumberId, token, Map.of(
                "messaging_product", "whatsapp",
                "to", para,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "button",
                        "body", Map.of("text", cuerpo),
                        "action", Map.of("buttons", botones))));
    }

    /**
     * Plantilla aprobada por Meta. Es la única forma de escribirle a alguien
     * FUERA de la ventana de 24 horas: recordatorios, avisos de cupo libre.
     */
    public void plantilla(String phoneNumberId, String token, String para,
                          String nombrePlantilla, String idioma, List<String> parametros) {

        List<Map<String, Object>> componentes = parametros.isEmpty() ? List.of() : List.of(
                Map.of("type", "body",
                       "parameters", parametros.stream()
                               .map(p -> Map.<String, Object>of("type", "text", "text", p))
                               .toList()));

        if (simular) {
            guardar(para, "(plantilla " + nombrePlantilla + ") " + String.join(" | ", parametros));
            return;
        }

        Map<String, Object> plantilla = new LinkedHashMap<>();
        plantilla.put("name", nombrePlantilla);
        plantilla.put("language", Map.of("code", idioma));
        if (!componentes.isEmpty()) plantilla.put("components", componentes);

        enviar(phoneNumberId, token, Map.of(
                "messaging_product", "whatsapp",
                "to", para,
                "type", "template",
                "template", plantilla));
    }

    private String recortar(String texto, int max) {
        if (texto == null) return "";
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }
}
