package com.agenda.agenda;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "cliente")
@Getter @Setter @NoArgsConstructor
public class Cliente {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(nullable = false) private String nombre;
    @Column(nullable = false) private String telefono;
    private String notas;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();
}
