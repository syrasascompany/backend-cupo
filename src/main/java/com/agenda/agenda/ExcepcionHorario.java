package com.agenda.agenda;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity @Table(name = "excepcion_horario")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ExcepcionHorario {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profesional_id", nullable = false) private Long profesionalId;
    @Column(nullable = false) private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private TipoExcepcion tipo;

    @Column(name = "hora_inicio") private LocalTime horaInicio;
    @Column(name = "hora_fin") private LocalTime horaFin;
    private String motivo;
}
