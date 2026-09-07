package com.agenda.agenda;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "cita")
@Getter @Setter @NoArgsConstructor
public class Cita {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(name = "profesional_id", nullable = false) private Long profesionalId;
    @Column(name = "servicio_id", nullable = false) private Long servicioId;
    @Column(name = "cliente_id") private Long clienteId;

    @Column(nullable = false) private Instant inicio;
    @Column(nullable = false) private Instant fin;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private EstadoCita estado = EstadoCita.CONFIRMADA;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private OrigenCita origen = OrigenCita.PANEL;

    private String notas;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn = Instant.now();

    public boolean estaViva() {
        return estado == EstadoCita.CONFIRMADA || estado == EstadoCita.FINALIZADA;
    }
}
