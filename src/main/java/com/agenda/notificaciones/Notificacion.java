package com.agenda.notificaciones;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Registro de lo enviado. Sirve para no repetir recordatorios. */
@Entity @Table(name = "notificacion")
@Getter @Setter @NoArgsConstructor
public class Notificacion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(name = "cita_id") private Long citaId;

    @Column(nullable = false) private String tipo;      // RECORDATORIO_24H, RECORDATORIO_2H, CUPO_LIBRE...
    @Column(nullable = false) private String destino;
    @Column(nullable = false) private String estado = "ENVIADA";
    private String detalle;

    @Column(name = "enviada_en", nullable = false, updatable = false)
    private Instant enviadaEn = Instant.now();
}
