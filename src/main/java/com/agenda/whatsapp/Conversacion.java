package com.agenda.whatsapp;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/** En qué paso va cada clienta. El bot no recuerda nada por sí solo. */
@Entity @Table(name = "conversacion")
@Getter @Setter @NoArgsConstructor
public class Conversacion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(nullable = false) private String telefono;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PasoBot paso = PasoBot.INICIO;

    @Column(name = "servicio_id") private Long servicioId;
    @Column(name = "profesional_id") private Long profesionalId;
    private LocalDate fecha;
    @Column(name = "inicio_elegido") private Instant inicioElegido;
    @Column(name = "nombre_cliente") private String nombreCliente;

    /** La cita que la clienta está moviendo o cancelando. */
    @Column(name = "cita_id") private Long citaId;

    @Column(name = "ultimo_mensaje", nullable = false)
    private Instant ultimoMensaje = Instant.now();

    /**
     * Mientras esta fecha no pase, el bot no responde: hay una persona
     * atendiendo esta conversación desde el celular.
     */
    @Column(name = "pausado_hasta") private Instant pausadoHasta;

    public boolean estaPausada() {
        return pausadoHasta != null && pausadoHasta.isAfter(Instant.now());
    }

    public void reiniciar() {
        paso = PasoBot.INICIO;
        servicioId = null; profesionalId = null;
        fecha = null; inicioElegido = null; citaId = null;
    }
}