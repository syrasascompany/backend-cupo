package com.agenda.whatsapp;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Meta reintenta los webhooks. Esto impide agendar dos veces lo mismo. */
@Entity @Table(name = "mensaje_procesado")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MensajeProcesado {

    @Id
    @Column(name = "wa_message_id")
    private String waMessageId;

    @Column(name = "recibido_en", nullable = false)
    private Instant recibidoEn = Instant.now();
}
