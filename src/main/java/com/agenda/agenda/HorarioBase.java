package com.agenda.agenda;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

/** Horario normal de una profesional en un día de la semana (1=lunes). */
@Entity @Table(name = "horario_base")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class HorarioBase {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profesional_id", nullable = false) private Long profesionalId;
    @Column(name = "dia_semana", nullable = false) private Short diaSemana;
    @Column(name = "hora_inicio", nullable = false) private LocalTime horaInicio;
    @Column(name = "hora_fin", nullable = false) private LocalTime horaFin;
}
