package com.agenda.espera;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name = "lista_espera")
@Getter @Setter @NoArgsConstructor
public class ListaEspera {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(name = "cliente_id") private Long clienteId;
    @Column(name = "servicio_id", nullable = false) private Long servicioId;

    /** Nulo significa "me sirve cualquier profesional". */
    @Column(name = "profesional_id") private Long profesionalId;

    @Column(nullable = false) private LocalDate desde;
    @Column(nullable = false) private LocalDate hasta;

    @Column(nullable = false) private String telefono;
    private String nombre;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private EstadoEspera estado = EstadoEspera.ESPERANDO;

    @Column(name = "avisado_en") private Instant avisadoEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();
}
